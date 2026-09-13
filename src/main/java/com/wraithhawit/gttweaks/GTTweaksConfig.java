package com.wraithhawit.gttweaks;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings, stored in {@code config/gttweaks-client.toml} and editable from the mod list's
 * Config button. NeoForge reloads the file while the game runs, so a changed value applies to the
 * next scroll without a restart.
 */
public final class GTTweaksConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    private static final ModConfigSpec.DoubleValue PREVIEW_ZOOM_SPEED;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PREVIEW_ZOOM_SPEED = builder
                .comment("Scroll-wheel zoom speed in multiblock previews, as a multiple of ModularUI's own step",
                        "(1/12 block of camera distance per notch). 1.0 is ModularUI's speed; the default 10.0",
                        "makes large structures, which start dozens of blocks away, practical to zoom.")
                .translation("gttweaks.configuration.previewZoomSpeed")
                .defineInRange("previewZoomSpeed", 10.0D, 0.1D, 100.0D);
        CLIENT_SPEC = builder.build();
    }

    private GTTweaksConfig() {
    }

    /** The configured multiplier, or its default before the client config has loaded. */
    public static double previewZoomSpeed() {
        return CLIENT_SPEC.isLoaded() ? PREVIEW_ZOOM_SPEED.get() : PREVIEW_ZOOM_SPEED.getDefault();
    }
}
