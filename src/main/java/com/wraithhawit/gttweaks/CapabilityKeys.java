package com.wraithhawit.gttweaks;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets recipe JSON written for GregTech 7.x name capabilities without a namespace.
 *
 * <p>7.x keyed a recipe's {@code inputs}/{@code outputs}/{@code tickInputs} maps by the capability's
 * plain name ({@code "item"}, {@code "fluid"}, {@code "eu"}). The 8.0 snapshot keys them by registry
 * id ({@code gtceu:item} ...) and reads them with {@code RECIPE_CAPABILITIES.byNameCodec()}, which
 * resolves a bare {@code "item"} to {@code minecraft:item} and rejects the whole recipe. Every
 * GregTech recipe Productive Bees 13.13.5 ships is written that way, so none of them load.
 *
 * <p>Only decoding changes, and only for a bare string: it is read in the {@code gtceu} namespace,
 * where every built-in capability lives. Anything already namespaced — including another mod's
 * capability — is passed through untouched, and encoding always writes the full id, so recipes
 * GregTech or KubeJS serialize are unaffected.
 */
public final class CapabilityKeys {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String NAMESPACE = "gtceu";

    /** Bare names already reported, so a pack with hundreds of such recipes logs each name once. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private CapabilityKeys() {}

    /** Wraps a capability-name codec so bare names decode in the {@code gtceu} namespace. */
    public static <K> Codec<K> acceptBareNames(Codec<K> named) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<K, T>> decode(DynamicOps<T> ops, T input) {
                return named.decode(ops, qualify(ops, input));
            }

            @Override
            public <T> DataResult<T> encode(K value, DynamicOps<T> ops, T prefix) {
                return named.encode(value, ops, prefix);
            }

            @Override
            public String toString() {
                return "BareCapabilityNames[" + named + "]";
            }
        };
    }

    static <T> T qualify(DynamicOps<T> ops, T input) {
        Optional<String> name = ops.getStringValue(input).result();
        if (name.isEmpty() || name.get().indexOf(':') >= 0) {
            return input;
        }
        String qualified = NAMESPACE + ":" + name.get();
        if (REPORTED.add(name.get())) {
            LOGGER.info("[gttweaks] reading bare recipe capability key '{}' as {}", name.get(), qualified);
        }
        return ops.createString(qualified);
    }
}
