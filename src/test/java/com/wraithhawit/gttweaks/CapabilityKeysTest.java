package com.wraithhawit.gttweaks;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityKeysTest {

    /** Stands in for {@code RECIPE_CAPABILITIES.byNameCodec()}: decodes whatever name it is handed. */
    private static final Codec<String> NAMES = CapabilityKeys.acceptBareNames(Codec.STRING);

    private static String decode(JsonElement json) {
        return NAMES.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test void bareNameIsReadInTheGregTechNamespace() {
        assertEquals("gtceu:item", decode(new JsonPrimitive("item")));
        assertEquals("gtceu:fluid", decode(new JsonPrimitive("fluid")));
        assertEquals("gtceu:eu", decode(new JsonPrimitive("eu")));
    }

    @Test void namespacedNamesPassThroughUnchanged() {
        assertEquals("gtceu:item", decode(new JsonPrimitive("gtceu:item")));
        // Another mod's capability must not be forced into gtceu.
        assertEquals("othermod:mana", decode(new JsonPrimitive("othermod:mana")));
    }

    @Test void encodingStillWritesTheFullId() {
        JsonElement written = NAMES.encodeStart(JsonOps.INSTANCE, "gtceu:item").getOrThrow();
        assertEquals(new JsonPrimitive("gtceu:item"), written);
    }

    @Test void nonStringInputReachesTheOriginalCodecUntouched() {
        // The wrapped codec, not the wrapper, decides how to fail on a malformed key.
        assertTrue(NAMES.parse(JsonOps.INSTANCE, new JsonPrimitive(5)).error().isPresent());
    }
}
