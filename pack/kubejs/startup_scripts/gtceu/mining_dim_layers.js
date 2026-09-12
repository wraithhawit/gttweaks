// priority: 90
//
// Ported from All the Mods 9's kubejs/startup_scripts/mining_dim_layers.js (original by AllTheMods
// Staff / a community contributor). This is a rewrite, not a copy - ATM9's one statement cannot
// work on GTCEu 8 in any form:
//
//   ATM9:  GTWorldGenLayers.STONE.levels = [new ResourceLocation("minecraft", "overworld"),
//                                           new ResourceLocation("allthemodium", "mining")]
//
// On GTCEu 8, SimpleWorldGenLayer.levels is a `private final Set<ResourceKey<Level>>` built with
// Set.of(Level.OVERWORLD). Private, final, immutable, and holding ResourceKeys rather than
// ResourceLocations - each of those four alone defeats the assignment, and getLevels().add(...)
// throws UnsupportedOperationException. A built-in layer cannot be widened at all.
//
// What replaced it is GTCEuStartupEvents.worldGenLayers. Its event.create(id, builder) builds a
// SimpleWorldGenLayer, and the SimpleWorldGenLayer CONSTRUCTOR registers itself into
// GTRegistries.WORLD_GEN_LAYERS - so a layer made here is addressable by id later, which is how
// server_scripts/mods/gtceu/mining_dim_ores.js gets hold of it. So rather than widening
// gtceu:stone we register kubejs:mining_stone beside it and move every vein onto it.
//
// The four new layers reuse the built-in layers' own RuleTests through getTarget(), so they
// replace exactly the blocks GregTech would have replaced: stone, deepslate, netherrack, end
// stone. That is what makes ATM9's Y-band scheme work in the first place - the mining dimension
// is banded out of those same four rocks by depth.
//
// Each new layer keeps its built-in counterpart's home dimension alongside allthemodium:mining,
// mirroring ATM9. It is belt-and-braces: mining_dim_ores.js sets every vein's dimension filter to
// the mining dimension anyway, so the home dimension is never reached through these layers.

GTCEuStartupEvents.worldGenLayers(event => {
    const $ResourceKey = Java.loadClass('net.minecraft.resources.ResourceKey')
    const $ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    const $Registries = Java.loadClass('net.minecraft.core.registries.Registries')
    const $ArrayList = Java.loadClass('java.util.ArrayList')

    // ResourceLocation is no longer constructible on 1.21 - `new ResourceLocation(ns, path)` is
    // gone in favour of the static parse/fromNamespaceAndPath factories.
    const dimensionKey = id => $ResourceKey.create($Registries.DIMENSION, $ResourceLocation.parse(id))

    // Built-in layer -> the mining-dimension layer that stands in for it. mining_dim_ores.js
    // carries the same four ids; keep the two lists in step.
    const layers = [
        { builtIn: GTWorldGenLayers.STONE,      id: 'kubejs:mining_stone',      home: 'minecraft:overworld' },
        { builtIn: GTWorldGenLayers.DEEPSLATE,  id: 'kubejs:mining_deepslate',  home: 'minecraft:overworld' },
        { builtIn: GTWorldGenLayers.NETHERRACK, id: 'kubejs:mining_netherrack', home: 'minecraft:the_nether' },
        { builtIn: GTWorldGenLayers.ENDSTONE,   id: 'kubejs:mining_endstone',   home: 'minecraft:the_end' },
    ]

    layers.forEach(spec => {
        const dimensions = new $ArrayList()
        dimensions.add(dimensionKey(spec.home))
        dimensions.add(dimensionKey('allthemodium:mining'))

        event.create($ResourceLocation.parse(spec.id), builder => {
            // targets() is typed RuleTestSupplier..., but do NOT pass a function. GregTech
            // registers a KubeJS type wrapper for it that hands whatever you pass to
            // BlockStatePredicate.wrapRuleTest, so a JS arrow function is stored verbatim and
            // stringified into ResourceLocation.parse the first time GregTech asks for the
            // target - which happens during CHUNK GENERATION, on a worker thread, long after
            // every load-time check has passed clean. It reads:
            //     ResourceLocationException: Non [a-z0-9/._-] character in path of location:
            //     minecraft:ArrowFunction (0) => {...}
            // and it kills chunk generation outright. Cost a broken world on 2026-08-31.
            //
            // wrapRuleTest passes a real RuleTest straight through, so hand it the built-in
            // layer's own RuleTest object. All four built-ins are TagMatchTests over the
            // stone / deepslate / nether_carver / end_stone ore-replaceable tags; taking the
            // object rather than naming those tags means this cannot drift from whatever
            // GregTech actually uses.
            builder.targets(spec.builtIn.getTarget())
            builder.dimensions(dimensions)
        })
    })

    console.info(`[mining-dim] registered ${layers.length} world gen layers for allthemodium:mining`)
})
