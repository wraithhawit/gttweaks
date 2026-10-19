package com.wraithhawit.gttweaks;

import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.OptionCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the staged JourneyMap API 2.0.0 classes the way GregTech's {@code JourneymapOptions} and
 * JourneyMap 6.0.9's client {@code OptionsDisplayFactory} do: GregTech builds {@code common.option}
 * toggles inside the registry event, then the factory binds whatever sits in the deprecated
 * {@code client.option} registry with a config of its own.
 */
@SuppressWarnings("removal")
class JourneyMapLayerOptionsTest {

    private static Map<String, BooleanOption> gregTechLayers() {
        final OptionCategory category = new OptionCategory("gtceu", "gtceu.journeymap.options.layers");
        final Map<String, BooleanOption> layers = new LinkedHashMap<>();
        for (final String layer : new String[] {"ore_veins", "bedrock_fluids"}) {
            layers.put(layer, new BooleanOption(category, layer, "gtceu.button." + layer, false));
        }
        return layers;
    }

    /** The client factory's pass: setConfig on every deprecated-registry option, here a plain holder. */
    private static Map<String, Holder> bindLikeClientFactory() throws ReflectiveOperationException {
        final Method setConfig = journeymap.api.v2.client.option.Option.class
                .getDeclaredMethod("setConfig", journeymap.api.v2.client.option.Config.class);
        setConfig.setAccessible(true);
        final Map<String, Holder> fields = new LinkedHashMap<>();
        for (final var entry : journeymap.api.v2.client.option.OptionsRegistry.OPTION_REGISTRY
                .get("gtceu").entrySet()) {
            final Holder field = new Holder((Boolean) entry.getValue().getDefaultValue());
            setConfig.invoke(entry.getValue(), field);
            fields.put(entry.getKey(), field);
        }
        return fields;
    }

    @Test
    void unpatchedLayerOptionThrowsAsInTheLog() {
        final BooleanOption option = gregTechLayers().get("ore_veins");
        final NullPointerException e = assertThrows(NullPointerException.class, option::get);
        assertTrue(e.getMessage().contains("this.config"), e.getMessage());
    }

    @Test
    void boundOptionReadsBeforeAndAfterTheClientFactoryPass() throws ReflectiveOperationException {
        final Map<String, BooleanOption> layers = gregTechLayers();
        JourneyMapLayerOptions.bind(layers);

        // Between the event and the factory pass: no mirror config yet, still no throw.
        assertFalse(layers.get("ore_veins").get());
        layers.get("bedrock_fluids").set(true);
        assertTrue(layers.get("bedrock_fluids").get());

        final Map<String, Holder> fields = bindLikeClientFactory();
        assertEquals(java.util.Set.of("ore_veins", "bedrock_fluids"), fields.keySet());

        // After it: reads and writes go to JourneyMap's own field, which is what gets saved.
        layers.get("ore_veins").set(true);
        assertTrue(fields.get("ore_veins").value);
        fields.get("ore_veins").value = false;
        assertFalse(layers.get("ore_veins").get());
    }

    @Test
    void aLaterGenerationReplacesTheEarlierMirrors() throws ReflectiveOperationException {
        final Map<String, BooleanOption> first = gregTechLayers();
        JourneyMapLayerOptions.bind(first);
        bindLikeClientFactory();

        // A world join posts the event again and GregTech builds a new set.
        final Map<String, BooleanOption> second = gregTechLayers();
        JourneyMapLayerOptions.bind(second);
        final Map<String, Holder> fields = bindLikeClientFactory();

        second.get("ore_veins").set(true);
        assertTrue(fields.get("ore_veins").value);
    }

    private static final class Holder implements journeymap.api.v2.client.option.Config<Boolean> {
        Boolean value;

        Holder(final Boolean value) {
            this.value = value;
        }

        @Override
        public Boolean get() {
            return value;
        }

        @Override
        public journeymap.api.v2.client.option.Config<Boolean> set(final Boolean value) {
            this.value = value;
            return this;
        }
    }
}
