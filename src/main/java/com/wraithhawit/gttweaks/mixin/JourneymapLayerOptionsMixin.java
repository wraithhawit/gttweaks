package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.integration.map.journeymap.JourneymapOptions;
import com.mojang.logging.LogUtils;
import com.wraithhawit.gttweaks.JourneyMapLayerOptions;
import journeymap.api.v2.common.option.BooleanOption;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Binds GregTech's JourneyMap layer options as soon as they are built; see
 * {@link JourneyMapLayerOptions} for why JourneyMap 6.0.9 never does.
 *
 * <p>The constructor runs inside JourneyMap's synchronous {@code OptionsRegistryEvent} post, ahead of
 * the client factory's binding pass. Anything thrown here would abort that post and the pass for
 * every other addon, so a failure is logged and swallowed.
 */
@Mixin(JourneymapOptions.class)
public abstract class JourneymapLayerOptionsMixin {

    @Unique
    private static final Logger gttweaks$LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private Map<String, BooleanOption> layerOptions;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gttweaks$bindLayerOptions(CallbackInfo ci) {
        try {
            JourneyMapLayerOptions.bind(layerOptions);
        } catch (final Throwable t) {
            gttweaks$LOGGER.error("[gttweaks] could not bind GregTech's JourneyMap layer options", t);
        }
    }
}
