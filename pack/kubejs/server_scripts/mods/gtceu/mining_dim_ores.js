// Ported from All the Mods 9's kubejs/server_scripts/mods/gtceu/mining_dim_ores.js (original by
// AllTheMods Staff / a community contributor). It moves every GregTech ore vein out of the
// overworld/nether/end and into allthemodium:mining, stacked in Y-bands by the rock the vein was
// written for, and adds one new fluorite/sulfur/gypsum/dolomite dike vein.
//
// It needs startup_scripts/gtceu/mining_dim_layers.js, which registers the four kubejs:mining_*
// world gen layers this reassigns veins onto. ATM9 instead widened the built-in layers in place;
// see that file's header for why GTCEu 8 makes that impossible.
//
// WORLDGEN ONLY AFFECTS NEWLY GENERATED CHUNKS. Mining-dimension chunks that already exist keep
// whatever they generated with, so an established save ends up with two ore distributions either
// side of the explored boundary. That is understood and accepted.
//
// Five GTCEu 8 breaks beyond the layer rewrite, four of them silent:
//
//  1. vein.dimensions() takes a Set<ResourceKey<Level>> and vein.biomes() a TagKey<Biome>; ATM9
//     passed the strings 'allthemodium:mining' and '#allthemodium:mining_features/mining_biomes'.
//     There is no string overload on GTOreDefinition, so both are built explicitly.
//  2. The dike branch is rewritten. ATM9 walked getAllEntries() and fed each entry back through
//     withBlock(). On GTCEu 8 that no longer type-checks - getAllEntries() hands back
//     VeinGenerator.VeinEntry, whose vein() is an Either<BlockState, Material>, while
//     DikeBlockDefinition wants an Either<List<TargetBlockState>, Material>. withBlock() also
//     APPENDS rather than replaces, so the original definitions kept their old Y range regardless.
//     The public `blocks` list is rebuilt instead.
//  3. event.add() hands the callback a raw GTOreDefinition, not GT7's OreVeinDefinitionBuilderJS.
//     Same method names, but nothing infers the generator's Y range from heightRangeUniform any
//     more, and .layer() takes a layer object rather than a name - see the fluorite vein.
//  4. veinGenerator() has two same-arity overloads (ResourceLocation and VeinGenerator), so it
//     still needs the explicit-signature call syntax ATM9 used, as does GTDikeBlockDefinition,
//     which has three four-argument constructors.
//  5. GTMaterials.get() now runs its argument through ResourceLocation.parse(), so ATM9's bare
//     GTMaterials.get('fluorite') looks up minecraft:fluorite, misses, logs one WARN line and
//     returns Material.NULL rather than throwing. The fluorite vein would have registered and
//     generated nothing but null blocks. Every name here is namespaced, and materialOrThrow()
//     below turns a miss back into a hard failure.

GTCEuServerEvents.oreVeins(event => {
    const $ResourceKey = Java.loadClass('net.minecraft.resources.ResourceKey')
    const $ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    const $Registries = Java.loadClass('net.minecraft.core.registries.Registries')
    const $TagKey = Java.loadClass('net.minecraft.tags.TagKey')
    const $HashSet = Java.loadClass('java.util.HashSet')
    const $ArrayList = Java.loadClass('java.util.ArrayList')
    const $VeinedVeinGenerator = Java.loadClass('com.gregtechceu.gtceu.api.data.worldgen.generator.veins.VeinedVeinGenerator')
    const $DikeVeinGenerator = Java.loadClass('com.gregtechceu.gtceu.api.data.worldgen.generator.veins.DikeVeinGenerator')
    const $CuboidVeinGenerator = Java.loadClass('com.gregtechceu.gtceu.api.data.worldgen.generator.veins.CuboidVeinGenerator')

    const MINING_DIM = $ResourceKey.create($Registries.DIMENSION, $ResourceLocation.parse('allthemodium:mining'))
    const MINING_BIOMES = $TagKey.create($Registries.BIOME, $ResourceLocation.parse('allthemodium:mining_features/mining_biomes'))

    const layerFor = id => GTRegistries.WORLD_GEN_LAYERS.get($ResourceLocation.parse(id))

    // GTMaterials.get() answers a miss with Material.NULL and a single WARN line, which is easy to
    // scroll past and leaves a vein made of nothing. fluorite and dolomite are ATM9's own, created
    // by startup_scripts/gtceu/material_modification.js; sulfur and gypsum are GregTech's.
    const materialOrThrow = id => {
        const material = GTMaterials.get(id)
        if (material === null || material.isNull()) {
            throw new Error(`[mining-dim] material ${id} does not exist - cannot build kubejs:fluorite_vein`)
        }
        return material
    }

    // Keyed on the built-in layer's serialized name, which is its registry id. The Y bands are
    // ATM9's, and match how the mining dimension itself is stacked.
    const bands = {
        'gtceu:endstone':   { startY: -63, endY: 0,   layer: layerFor('kubejs:mining_endstone') },
        'gtceu:netherrack': { startY: 1,   endY: 64,  layer: layerFor('kubejs:mining_netherrack') },
        'gtceu:deepslate':  { startY: 65,  endY: 128, layer: layerFor('kubejs:mining_deepslate') },
        'gtceu:stone':      { startY: 129, endY: 248, layer: layerFor('kubejs:mining_stone') },
    }

    // A layer this script does not know about cannot be moved into the mining dimension, so the
    // vein is parked in a one-block band at build height, exactly as ATM9 did - it stays
    // registered but never generates.
    const PARKED = { startY: 319, endY: 320, layer: null }

    const missingLayers = Object.keys(bands).filter(k => bands[k].layer === null)
    if (missingLayers.length > 0) {
        // Loud, because the alternative is silently parking every vein in the game at Y319.
        console.error(`[mining-dim] world gen layers missing: ${missingLayers.join(', ')} - is startup_scripts/gtceu/mining_dim_layers.js loaded? Aborting, ore veins left untouched.`)
        return
    }

    // Force the lazy half of each layer NOW. A layer's RuleTest is not built when the layer is
    // registered - GregTech asks for it during chunk generation, on a worker thread. A bad
    // target therefore passes every load-time check and then kills worldgen instead, which is
    // exactly how a broken target shipped on 2026-08-31: 18/18 scripts, 0 errors, 40 veins
    // moved, and an unplayable world. Calling getTarget() here turns that into a load-time
    // abort with the veins left alone.
    const badTargets = []
    Object.keys(bands).forEach(k => {
        try {
            if (bands[k].layer.getTarget() === null) badTargets.push(k + ' (null target)')
        } catch (e) {
            badTargets.push(`${k} (${String(e).slice(0, 120)})`)
        }
    })
    if (badTargets.length > 0) {
        console.error(`[mining-dim] world gen layer targets are broken and would crash chunk generation: ${badTargets.join('; ')}. Aborting, ore veins left untouched.`)
        return
    }

    let moved = 0
    let parked = 0
    const parkedLayers = {}

    // Every declaration in this callback is hoisted to the top on purpose. Rhino 2101 does not
    // give a `const` inside a nested block its own scope per call - the second vein to reach the
    // dike branch died on "TypeError: redeclaration of var rebuilt", which aborts the whole
    // GTCEuServerEvents.oreVeins callback and leaves every vein unmodified.
    event.modifyAll((veinId, vein) => {
        let layerName = vein.layer().getSerializedName()
        let band = bands[layerName] || PARKED
        let veinGen = vein.veinGenerator()
        let rebuilt = null
        let dimensions = new $HashSet()

        if (band.layer === null) {
            parked++
            parkedLayers[layerName] = (parkedLayers[layerName] || 0) + 1
        } else {
            vein.layer(band.layer)
            moved++
        }

        if (veinGen instanceof $VeinedVeinGenerator) {
            veinGen = veinGen.copy()
            veinGen.minYLevel(band.startY)
            veinGen.maxYLevel(band.endY)
        } else if (veinGen instanceof $DikeVeinGenerator) {
            veinGen = veinGen.copy()
            veinGen.minYLevel(band.startY)
            veinGen.maxYLevel(band.endY)
            // Each block definition carries its own Y range on top of the generator's, so they
            // have to move too or the dike generates into an empty band. Rebuild rather than
            // append - see break 2 in the header.
            rebuilt = new $ArrayList()
            veinGen.blocks.forEach(block => {
                rebuilt.add(new GTDikeBlockDefinition['(com.mojang.datafixers.util.Either,int,int,int)'](
                    block.block(), block.weight(), band.startY, band.endY))
            })
            veinGen.blocks = rebuilt
        } else if (veinGen instanceof $CuboidVeinGenerator) {
            veinGen = veinGen.copy()
            veinGen.minY(band.startY)
            veinGen.maxY(band.endY)
        }

        dimensions.add(MINING_DIM)

        vein.heightRangeUniform(band.startY, band.endY)
        vein.dimensions(dimensions)
        vein.biomes(MINING_BIOMES)
        vein['veinGenerator(com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator)'](veinGen)
    })

    event.add($ResourceLocation.parse('kubejs:fluorite_vein'), vein => {
        let dimensions = new $HashSet()
        dimensions.add(MINING_DIM)

        vein.clusterSize(35)
        vein.weight(30)
        vein.density(0.75)
        vein.discardChanceOnAirExposure(0.0)
        vein.layer(bands['gtceu:deepslate'].layer)
        vein.dimensions(dimensions)
        vein.biomes(MINING_BIOMES)
        vein.heightRangeUniform(65, 128)
        vein.dikeVeinGenerator(generator => {
            // minYLevel/maxYLevel are new here. GT7's KJS builder inferred them; a raw
            // GTOreDefinition leaves them at the default 0, and DikeVeinGenerator.generate()
            // walks minYLevel..maxYLevel, so without these the vein places nothing at all.
            generator.minYLevel(65)
            generator.maxYLevel(128)
            generator.withBlock(new GTDikeBlockDefinition['(com.gregtechceu.gtceu.api.data.chemical.material.Material,int,int,int)'](materialOrThrow('gtceu:fluorite'), 3, 65, 128))
            generator.withBlock(new GTDikeBlockDefinition['(com.gregtechceu.gtceu.api.data.chemical.material.Material,int,int,int)'](materialOrThrow('gtceu:sulfur'), 1, 65, 128))
            generator.withBlock(new GTDikeBlockDefinition['(com.gregtechceu.gtceu.api.data.chemical.material.Material,int,int,int)'](materialOrThrow('gtceu:gypsum'), 2, 65, 128))
            generator.withBlock(new GTDikeBlockDefinition['(com.gregtechceu.gtceu.api.data.chemical.material.Material,int,int,int)'](materialOrThrow('gtceu:dolomite'), 1, 65, 128))
        })
    })

    console.info(`[mining-dim] ${moved} ore vein(s) moved to allthemodium:mining, 1 added (kubejs:fluorite_vein)`)
    if (parked > 0) {
        console.warn(`[mining-dim] ${parked} vein(s) parked at Y319 - unrecognised layers: ${JSON.stringify(parkedLayers)}`)
    }
})
