package com.wraithhawit.gttweaks;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * View maths for ModularUI's schema preview, kept free of Minecraft classes so it can be tested
 * against the same composition the shader performs.
 *
 * <p>ModularUI subtracts the camera position twice. {@code BaseSchemaRenderer.setupCamera} builds
 * the model-view with {@code MatrixUtils.lookAt(eye, target)}, which is {@code R * T(-eye)}, and
 * {@code renderBlocks} also sets {@code ChunkOffset = -eye}. The vanilla block shader draws
 * {@code ProjMat * ModelViewMat * (Position + ChunkOffset)}, so a block at {@code v} lands at
 * {@code R * (v - 2 * eye)}: a correct orbit camera, but around {@code 2 * lookAt} at twice the
 * distance. Block entities ({@code translate(pos - eye)}), the hover highlight
 * ({@code createWorldRenderPose}) and picking ({@code rebuildProjection}) all make the same double
 * subtraction, which is why they agree with each other and with the misplaced model.
 */
public final class SchemaView {

    /**
     * The double subtraction viewed everything from twice the requested distance. Fitted distances
     * are doubled so structures keep the on-screen size they had before the pivot was fixed.
     */
    public static final float FRAMING = 2.0F;

    private SchemaView() {
    }

    /**
     * Multiplies in the camera rotation only, as vanilla's level renderer does; the eye translation
     * is left to {@code ChunkOffset} and the other per-path offsets, which already apply it once.
     */
    public static Matrix4f lookAtRotationOnly(Matrix4f matrix, Vector3fc eye, Vector3fc target) {
        return matrix.lookAt(0.0F, 0.0F, 0.0F,
                target.x() - eye.x(), target.y() - eye.y(), target.z() - eye.z(),
                0.0F, 1.0F, 0.0F);
    }

    /** Centre of blocks {@code min..max} inclusive along one axis; block {@code p} spans {@code [p, p + 1]}. */
    public static float geometricCentre(int min, int max) {
        return (min + max + 1) / 2.0F;
    }

    /** Camera distance for a structure whose largest dimension is {@code largest} blocks. */
    public static float fitDistance(int largest) {
        // Sub-linear on purpose: apparent size goes as L/d, so d = L^0.875 gives big structures a
        // little more screen instead of rendering every machine the same size. The floor keeps a
        // one-block schema from putting the camera inside itself.
        return FRAMING * (float) Math.max(3.3D, Math.pow(largest, 0.875D));
    }

    /**
     * Normalised device coordinates of a block-space vertex, composed exactly as
     * {@code rendertype_solid.vsh} does: {@code ProjMat * ModelViewMat * (Position + ChunkOffset)}.
     */
    public static Vector3f projectBlockVertex(Matrix4f projection, Matrix4f modelView, Vector3fc chunkOffset,
                                              Vector3fc position, Vector3f dest) {
        return new Matrix4f(projection).mul(modelView).transformProject(
                position.x() + chunkOffset.x(), position.y() + chunkOffset.y(), position.z() + chunkOffset.z(),
                dest);
    }
}
