package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.drawable.schema.BaseSchemaRenderer;
import com.wraithhawit.gttweaks.SchemaView;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the multiblock preview rotate about its camera target instead of twice that point.
 *
 * <p>{@code setupCamera} ends with {@code MatrixUtils.lookAt(modelViewStack, camera.pos(),
 * camera.lookAt())}, a full view matrix {@code R * T(-eye)}. Every draw path then subtracts the
 * eye a second time: {@code renderBlocks} sets {@code ChunkOffset = -eye}, block entities translate
 * by {@code pos - eye}, the highlight pose is {@code translate(-eye)}, and picking appends
 * {@code translate(-eye)} in {@code rebuildProjection}. The result is an orbit about
 * {@code 2 * lookAt}; see {@link SchemaView}.
 *
 * <p>The redirect keeps the rotation and drops the translation, so all four paths subtract the eye
 * once and stay consistent with each other. The rotation part of the matrix is unchanged, so
 * orientation and lighting are not affected. This is the one call site of {@code MatrixUtils.lookAt}
 * in {@code BaseSchemaRenderer}.
 */
@Mixin(BaseSchemaRenderer.class)
public abstract class SchemaSingleEyeOffsetMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;

    @Redirect(
            method = "setupCamera(II)V",
            at = @At(value = "INVOKE",
                     target = "Lbrachy/modularui/utils/MatrixUtils;lookAt(Lorg/joml/Matrix4f;Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;)V"))
    private void gttweaks$lookAtWithoutEyeTranslation(Matrix4f modelView, Vector3fc eye, Vector3fc target) {
        if (!gttweaks$reported) {
            gttweaks$reported = true;
            // Unconditional first-frame measurement: where the camera target lands on screen under
            // the stock matrix and under this one. (0, 0) is the centre of the preview viewport.
            final Matrix4f projection = RenderSystem.getProjectionMatrix();
            final Vector3f chunkOffset = new Vector3f(eye).negate();
            final Matrix4f stock = new Matrix4f(modelView).lookAt(eye, target, new Vector3f(0.0F, 1.0F, 0.0F));
            final Matrix4f fixed = SchemaView.lookAtRotationOnly(new Matrix4f(modelView), eye, target);
            final Vector3f before = SchemaView.projectBlockVertex(projection, stock, chunkOffset, target, new Vector3f());
            final Vector3f after = SchemaView.projectBlockVertex(projection, fixed, chunkOffset, target, new Vector3f());
            LOGGER.info("[gttweaks] schema single eye offset: target ({},{},{}) eye ({},{},{}) -> NDC stock ({},{}) fixed ({},{})",
                    target.x(), target.y(), target.z(), eye.x(), eye.y(), eye.z(),
                    before.x, before.y, after.x, after.y);
        }
        SchemaView.lookAtRotationOnly(modelView, eye, target);
    }
}
