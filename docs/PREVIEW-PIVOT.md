# Multiblock preview rotates off-centre: eye subtracted twice

## Diagnosis

Staged jars verified against `libs/dependencies.sha256` (GTCEu `8.0.0-SNAPSHOT+20260822-141542-ef045f0`, ModularUI `3.3.1-SNAPSHOT`).

- `BaseSchemaRenderer.setupCamera(II)V` @194–210 calls `MatrixUtils.lookAt(Matrix4f, camera.pos(), camera.lookAt())`, which applies `R * T(-eye)` to the model-view stack.
- `BaseSchemaRenderer.renderBlocks` @276–319 sets `CHUNK_OFFSET = -camera.pos()`. Compiled vertices are in block space (`poseStack.translate(pos)` only).
- The vanilla `rendertype_solid.vsh` computes `ProjMat * ModelViewMat * vec4(Position + ChunkOffset, 1)`.

So a block at `v` is drawn at `R * (v - 2*eye)`. That is a correct orbit camera at twice the distance, around `2 * lookAt`. The rotation pivot is `2 * (focus + offset)`, not the structure, and the swing grows with the focus's distance from the origin. Block entities (`translate(pos - eye)`), the highlight (`createWorldRenderPose`, `translate(-eye)`) and picking (`rebuildProjection`, `P * MV * translate(-eye)`) repeat the same double subtraction. They all agree with each other, which is why picking looked right. It also explains 0.17.0: removing only the highlight's translate moved the highlight and left the blocks where they were.

A second, smaller error: `BlockPosUtil.getCenterF` is `min + |max - min| / 2`. That is half a block short of the geometric centre, because block `p` spans `[p, p+1]`.

## Fix

- `SchemaSingleEyeOffsetMixin` redirects that single `MatrixUtils.lookAt` call in `setupCamera` to a rotation-only lookAt (`SchemaView.lookAtRotationOnly`). All four draw paths now subtract the eye once. The rotation block is identical, so orientation and lighting do not change. On first use it logs the camera target's NDC under the stock and fixed matrices.
- `SchemaFitToStructureMixin` now aims at `(min + max + 1) / 2`. Its fitted distance is multiplied by `SchemaView.FRAMING = 2`, so on-screen size matches the double-distance view users were used to.

## Verification and limits

`SchemaViewTest` replays the bytecode's matrix composition using the staged `Camera` and `BlockPosUtil`, with bounds `(-7,3,12)..(4,9,15)`. The tests show:

- the stock pivot is exactly `2 * lookAt`, and the stock centre swings more than 0.3 NDC;
- the fix keeps the geometric centre at NDC (0,0) across 24 yaws, 5 pitches, 3 zoom distances, 2 pans, and both projections, with picking matching;
- with the doubled distance, the fixed view is the stock view shifted by one constant vector, with the same rotation.

These tests do not run the mixin transformation or draw a frame. In-game behaviour was confirmed separately in the ATM10(3) client on 2026-09-19: previews, including the cleanroom, rotate around their own centre through a full turn, and scroll zoom reads as a reasonable speed. Expected side effects: middle-drag pan and scroll zoom now move at half their former apparent speed, because the bug had doubled both. Any other ModularUI `BaseSchemaRenderer` user in the pack is also re-pivoted, and one that is not a `SchemaWidget` would render about 2x larger.
