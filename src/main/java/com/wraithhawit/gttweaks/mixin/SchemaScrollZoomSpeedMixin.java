package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widgets.SchemaWidget;
import com.wraithhawit.gttweaks.GTTweaksConfig;
import com.wraithhawit.gttweaks.SchemaView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Speeds up scroll-wheel zoom in multiblock previews by the configured multiplier.
 *
 * <p>ModularUI 3.3.1's {@code SchemaWidget.onMouseScrolled} is {@code incrementScale((float)
 * (-scroll / 12.0))}, and {@code scale} is the camera distance, so each notch moves the camera
 * 1/12 of a block. {@link SchemaFitToStructureMixin} starts large structures dozens of blocks away,
 * and the 0.29.0 pivot fix halved apparent zoom speed, so crossing that range took hundreds of
 * notches. Only this call's argument changes: the 0.001 distance floor in {@code incrementScale}
 * still applies, and other ModularUI widgets are untouched.
 */
@Mixin(SchemaWidget.class)
public abstract class SchemaScrollZoomSpeedMixin {

    @ModifyArg(
            method = "onMouseScrolled(DD)Z",
            at = @At(value = "INVOKE", target = "Lbrachy/modularui/widgets/SchemaWidget;incrementScale(F)V"))
    private float gttweaks$scaleScrollZoomStep(float step) {
        return SchemaView.scrollZoomStep(step, GTTweaksConfig.previewZoomSpeed());
    }
}
