package com.wraithhawit.gttweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Names the recipe categories whose icon came back null, once each.
 *
 * <p>A guard that only stops the crash leaves you knowing nothing. The category id is the whole
 * diagnosis: {@code gtceu:multiblock_info} means GregTech's own, while {@code gtceu:gregstar} or
 * {@code gtceu:greenhouse} means one of the recipe types this pack's KubeJS scripts create, and the
 * fix moves to those scripts.
 *
 * <p>Once each, because the render runs every frame and this is exactly the shape of thing that
 * fills a log with a million identical lines — which happened to this pack on 2026-08-31, when an
 * unguarded EMI NPE wrote 474MB.
 */
public final class EmiIconReport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private EmiIconReport() {}

    public static void nullIcon(ResourceLocation id, String which) {
        final String key = id + "/" + which;
        if (SEEN.add(key)) {
            LOGGER.error("[gttweaks] EMI category {} has no {} icon; drawing nothing instead of "
                    + "crashing. GregTech's CategoryIcon caches null when it is first built off the "
                    + "client thread, or before EMI/JEI report as loaded.", id, which);
        }
    }
}
