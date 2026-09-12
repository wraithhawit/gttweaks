package com.wraithhawit.gttweaks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The one thing a KubeJS script cannot do for itself: turn bytes into text.
 *
 * <p>Rhino has no route to it. {@code java.nio.charset.StandardCharsets} is refused by KubeJS's
 * class filter ("Class is not allowed by class filter!"), and {@code new java.lang.String(bytes,
 * "UTF-8")} does not resolve either — Rhino picks among String's byte-array constructors by
 * argument shape and lands on one that wants a {@code byte}, failing with "Cannot convert
 * NativeJavaArray to byte".
 *
 * <p>Used by {@code startup_scripts/gtceu/_read_json_from_mod.js}, which reads Productive Bees'
 * bee definitions straight out of its jar for the ported Apiary recipes.
 */
public final class ScriptIO {

    private ScriptIO() {}

    /** Reads a stream to the end as UTF-8 and closes it. */
    public static String readUtf8(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
