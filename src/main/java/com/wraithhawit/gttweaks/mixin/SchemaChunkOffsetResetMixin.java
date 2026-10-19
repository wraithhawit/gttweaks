package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.drawable.schema.BaseSchemaRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/**
 * Stops the multiblock preview from leaving its camera offset on the vanilla block shaders.
 *
 * <p>{@code BaseSchemaRenderer.renderBlocks} draws each layer with the shared vanilla shader for
 * that render type and sets its {@code ChunkOffset} uniform to {@code -eye}, then never sets it back.
 * Vanilla's {@code LevelRenderer.renderSectionLayer} would zero it on the next frame, but Sodium
 * replaces that pass with its own shaders, so the value stays. Anything drawn later with
 * {@code RenderType.cutout()} and friends then lands {@code -eye} away from where it should. Silent
 * Gear draws every tool through a {@code BlockEntityWithoutLevelRenderer} into
 * {@code RenderType.CUTOUT}, which is why its tools float out of their hotbar slot and the player's
 * hand after a preview has been rotated, while vanilla items, on the item shaders, stay put.
 *
 * <p>The reset goes to the method's own {@code ShaderInstance} local, not to whatever
 * {@code RenderSystem.getShader()} returns on the way out, and through {@link Uniform#set}, which
 * marks the uniform dirty so the next normal {@code apply()} uploads zero. {@code RETURN} also covers
 * the early exit for an invalid vertex buffer.
 */
@Mixin(BaseSchemaRenderer.class)
public abstract class SchemaChunkOffsetResetMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;

    @Inject(method = "renderBlocks", at = @At("RETURN"))
    private void gttweaks$resetChunkOffset(CallbackInfo ci, @Local ShaderInstance shader) {
        final Uniform offset = shader.CHUNK_OFFSET;
        if (offset == null) {
            return;
        }
        if (!gttweaks$reported) {
            gttweaks$reported = true;
            final FloatBuffer leaked = offset.getFloatBuffer();
            LOGGER.info("[gttweaks] schema ChunkOffset reset: {} had ({},{},{})",
                    shader.getName(), leaked.get(0), leaked.get(1), leaked.get(2));
        }
        offset.set(0.0F, 0.0F, 0.0F);
    }
}
