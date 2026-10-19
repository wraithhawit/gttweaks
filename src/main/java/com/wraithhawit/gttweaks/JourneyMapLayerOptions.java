package com.wraithhawit.gttweaks;

import com.mojang.logging.LogUtils;
import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.Config;
import journeymap.api.v2.common.option.OptionCategory;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gives GregTech's JourneyMap layer options a config on the client.
 *
 * <p>The JourneyMap API 2.0.0 carries two copies of its option classes: {@code v2.common.option},
 * which GregTech's {@code JourneymapOptions} builds its {@code ore_veins} and {@code bedrock_fluids}
 * toggles from, and {@code v2.client.option}, deprecated for removal. JourneyMap 6.0.9's client
 * {@code OptionsDisplayFactory} only binds the deprecated registry; only its server factory reads
 * {@code common.option}. On the client, every {@code OptionsRegistryEvent} (start-up and each world
 * join or leave) makes GregTech replace its options with fresh {@code common} ones that nothing ever
 * calls {@code setConfig} on, so the first prospecting packet hits
 * {@code Option.get -> this.config == null}. The throw escapes {@code GTClientCache.addVein} before
 * the vein reaches the cache, so the rest of that packet's veins are lost with it.
 *
 * <p>Each layer gets a mirror in the deprecated registry, which the client factory binds right after
 * the event returns, and the GregTech option's config forwards to it. The toggles then show up on
 * JourneyMap's addon options page and persist in {@code addon.gtceu.config}, as GregTech meant. Until
 * the mirror is bound, and if the deprecated classes are ever gone, the value lives in the forwarding
 * config itself, so a read never throws. If a later JourneyMap binds {@code common.option} itself, its
 * {@code setConfig} runs after this and simply replaces the forwarder.
 */
public final class JourneyMapLayerOptions {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean reported;
    private static boolean mirrorsUnavailable;

    private JourneyMapLayerOptions() {
    }

    public static void bind(final Map<String, BooleanOption> layers) {
        final List<String> mirrored = new ArrayList<>();
        for (final BooleanOption option : layers.values()) {
            final Forwarding config = new Forwarding(option.getDefaultValue());
            if (!mirrorsUnavailable) {
                try {
                    config.mirror = Mirror.of(option);
                    mirrored.add(option.getFieldName());
                } catch (final LinkageError e) {
                    mirrorsUnavailable = true;
                    LOGGER.warn("[gttweaks] JourneyMap's deprecated client options are gone ({}); GregTech map "
                            + "layer toggles will not be saved", e.toString());
                }
            }
            setConfig(option, config);
        }
        if (!reported) {
            reported = true;
            LOGGER.info("[gttweaks] bound GregTech JourneyMap layer options {} (mirrored: {})",
                    layers.keySet(), mirrored);
        }
    }

    private static void setConfig(final BooleanOption option, final Config<Boolean> config) {
        try {
            final Field field = journeymap.api.v2.common.option.Option.class.getDeclaredField("config");
            field.setAccessible(true);
            field.set(option, config);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("JourneyMap Option.config is not where gttweaks expects it", e);
        }
    }

    /** The config GregTech's option reads through: the mirror once JourneyMap has bound it, else its own value. */
    static final class Forwarding implements Config<Boolean> {
        private Boolean value;
        private Mirror mirror;

        Forwarding(final Boolean value) {
            this.value = value;
        }

        @Override
        public Boolean get() {
            final Mirror bound = mirror;
            return bound != null && bound.isBound() ? bound.get() : value;
        }

        @Override
        public Config<Boolean> set(final Boolean value) {
            final Mirror bound = mirror;
            if (bound != null && bound.isBound()) {
                bound.set(value);
            } else {
                this.value = value;
            }
            return this;
        }
    }

    /**
     * The deprecated-registry twin of one layer option. Kept in its own class so that only this class
     * fails to link if JourneyMap drops {@code v2.client.option}.
     */
    @SuppressWarnings("removal")
    static final class Mirror {
        private static final Field CONFIG;

        static {
            try {
                CONFIG = journeymap.api.v2.client.option.Option.class.getDeclaredField("config");
                CONFIG.setAccessible(true);
            } catch (final NoSuchFieldException e) {
                throw new NoSuchFieldError(e.getMessage());
            }
        }

        private final journeymap.api.v2.client.option.BooleanOption option;

        private Mirror(final journeymap.api.v2.client.option.BooleanOption option) {
            this.option = option;
        }

        static Mirror of(final BooleanOption source) {
            final OptionCategory category = source.getCategory();
            return new Mirror(new journeymap.api.v2.client.option.BooleanOption(
                    new journeymap.api.v2.client.option.OptionCategory(
                            category.getModId(), category.getLabel(), category.getToolTip()),
                    source.getFieldName(), source.getLabel(), source.getDefaultValue(), source.isMaster()));
        }

        boolean isBound() {
            try {
                return CONFIG.get(option) != null;
            } catch (final IllegalAccessException e) {
                return false;
            }
        }

        Boolean get() {
            return option.get();
        }

        void set(final Boolean value) {
            option.set(value);
        }
    }
}
