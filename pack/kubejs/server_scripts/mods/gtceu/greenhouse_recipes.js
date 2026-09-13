// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Original by AllTheMods Staff /
// community contributor; the license note from the original is kept at the foot of the file.
//
// Mechanical changes only — no recipe values were altered:
//   .itemInputs / .itemOutputs -> UNCHANGED. Renaming them to inputItems/outputItems is a
//       TRAP: itemInputs has exactly 1 overload, inputItems has 13, and Rhino cannot choose —
//       "The choice of Java method ... inputItems(MachineDefinition...)". Keep ATM9 names.
//   .itemOutputs(x)  -> .itemOutputs(x)     (alias; both exist)
//   .circuit(n)      -> UNCHANGED — see note below
//   .notConsumable(InputItem.of(x)) -> .notConsumableItem(x)
//       GTKubeRecipe.notConsumable now takes only (TagPrefix, Material); the item form was
//       split out as notConsumableItem(SizedIngredient), and InputItem no longer exists.
//   .chancedOutput(stack, chance, tierBoost) -> .chancedOutput(stack, chance)
//       GTCEu 8 dropped the tier-boost parameter from the ItemStack form. Every 3-arg overload
//
// JUDGEMENT CALL, flag for review: ATM9 wrote .EUt(MV) / .EUt(UHV) using bare globals that no
// GregTech version binds — not GT 7.2 in ATM9, not GT 8 here (checked registerBindings in both
// jars). Rewritten as GTValues.VA[GTValues.MV], which is the form ATM9?s own sibling scripts use
// (coil_multiblock_recipes.js). If those recipes were silently broken in ATM9 too, this is the
// first time they will actually work, so their EU cost is worth an eyeball in game.
//       that remains takes (TagPrefix, Material, ...) or a String, so the old call bound to
//       nothing. ATM9 passed the same value twice anyway, so no behaviour is lost.
//
// IMPORTANT: KubeJS scripts talk to GTRecipeSchema$GTKubeRecipe, NOT the Java GTRecipeBuilder.
// That class still declares circuit(int) and keeps itemInputs/itemOutputs as aliases; only
// GTRecipeBuilder renamed circuit -> circuitMeta. Verifying against the wrong class produced a
// "Cannot find function circuitMeta" failure on the first attempt at this port.
//
// This script READS botanypots recipe JSON but EMITS through GregTech's own recipe builder, so
// it is not affected by the null-GTRecipe.id crash that blocks the raw-JSON idiom on GTCEu 8.

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

ServerEvents.recipes(allthemods => {
    //////////////// machine controller ////////////////
    allthemods.shaped('gtceu:greenhouse', ['AWA', 'ASA', 'WAW'],
        {
            A: '#gtceu:circuits/mv',
            W: 'gtceu:copper_single_cable',
            S: 'gtceu:solid_machine_casing'
        }).id('gtceu:shaped/greenhouse')

    //////////////// greenhouse recipes ////////////////
    // ATM9 and ATM10 do not ship the same mod list, so some of the ~90 seed types named in the
    // tier lists below may not exist here. KubeJS aborts the ENTIRE ServerEvents.recipes callback
    // on the first unresolvable ingredient, which would silently discard every recipe after it —
    // that is how the greenhouse ended up with zero recipes once already. Skip and count instead.
    let ghMissing = 0
    function ghExists(id) {
        if (typeof id !== "string") return true
        return Item.exists(id.replace(/^[0-9]+x /, ""))
    }


    function grow(id, input, output, duration, boostItem, boosted) {
        if (!ghExists(input) || (boosted && !ghExists(boostItem))) { ghMissing++; return }
        if (boosted) {
            allthemods.recipes.gtceu.greenhouse(id)
                .circuit(2)
                .notConsumableItem(input)
                .itemInputs(boostItem)
                .inputFluids(Fluid.of('minecraft:water', 1000))
                .itemOutputs(output)
                .duration(duration / 2)
                .EUt(GTValues.VA[GTValues.MV])
        } else {
            allthemods.recipes.gtceu.greenhouse(id)
                .circuit(1)
                .notConsumableItem(input)
                .inputFluids(Fluid.of('minecraft:water', 1000))
                .itemOutputs(output)
                .duration(duration)
                .EUt(GTValues.VA[GTValues.MV])
        }
    }

    function grow_max(id, input, output, duration, boostItem, boosted, scale) {
        if (!ghExists(input) || (boosted && !ghExists(boostItem))) { ghMissing++; return }
        if (boosted) {
            allthemods.recipes.gtceu.greenhouse(id)
                .circuit(4)
                .notConsumableItem(input)
                .itemInputs(boostItem)
                .inputFluids(Fluid.of('minecraft:water', 1000 * scale))
                .itemOutputs(output)
                .duration(duration / 2)
                .EUt(GTValues.VA[GTValues.UHV])
        } else {
            allthemods.recipes.gtceu.greenhouse(id)
                .circuit(3)
                .notConsumableItem(input)
                .inputFluids(Fluid.of('minecraft:water', 1000 * scale))
                .itemOutputs(output)
                .duration(duration)
                .EUt(GTValues.VA[GTValues.UHV])
        }
    }

    // Copy every botany pot recipe into the greenhouse.
    //
    // REWRITTEN 2026-08-31. ATM9 read the crop JSON directly; Botany Pots 21.1 made that
    // impossible, and the first port's reader silently cloned nothing - 163 crops skipped every
    // reload. This reads the DESERIALIZED crop objects instead, via KubeJS's
    // KubeRecipe.getOriginalRecipe(), which is what actually survived the redesign.
    //
    // Four things the JSON reader could not see:
    //
    //  1. `type` in a KubeJS recipe filter matches the SERIALIZER id, not the recipe type. Crops
    //     serialize as either botanypots:crop (BasicCrop) or botanypots:block_derived_crop
    //     (BlockDerivedCrop). ATM9's single {type:'botanypots:crop'} filter therefore never saw
    //     the block-derived half at all - in a stripped harness that is 74 of 74 crops.
    //  2. `growthTicks` is `growTime` now, and on a block-derived crop it is not in the JSON at
    //     all. BlockDerivedCrop.Properties.toBasic() resolves the whole thing - seed, grow time
    //     and drops - against the block, which is the step that cannot be done from JSON.
    //  3. Drops are ItemDropProviders, not a literal list. SimpleDropProvider still carries an
    //     exact ItemStack and chance; LootTableDrops and BlockDrops carry a loot table, and the
    //     only thing readable from those is getDisplayItems() - the set of items the table CAN
    //     produce. Bookshelf's LootPoolEntryDescriptions has no weighted variant, so there is no
    //     chance to recover, only a set.
    //  4. Ingredient.getItems() is not reachable from Rhino. KubeJS's own getItemIds()/getFirst()
    //     are.
    //
    // JUDGEMENT CALL, flag for review: a loot-table crop's drops have no chances. Rather than
    // invent per-item numbers, one harvest is treated as yielding one stack drawn evenly from the
    // possible set - chance 1/n each. For the common single-item table (sugar cane, kelp, cactus)
    // that is exactly right; for a 4-item table like warped fungus it is an approximation that
    // keeps throughput at about one item per cycle instead of four. Crops with SimpleDropProvider
    // drops - which is what the productivetrees crops this rewrite exists to recover use - keep
    // their exact chances and stack counts. The two are counted separately below.
    const $BasicCrop = Java.loadClass('net.darkhax.botanypots.common.impl.data.recipe.crop.BasicCrop')
    const $BlockDerivedCrop = Java.loadClass('net.darkhax.botanypots.common.impl.data.recipe.crop.BlockDerivedCrop')
    const $SimpleDropProvider = Java.loadClass('net.darkhax.botanypots.common.impl.data.itemdrops.SimpleDropProvider')

    let botanyCloned = 0
    let botanyExact = 0
    let botanyApprox = 0
    let botanySkipped = 0
    let botanySkipReasons = {}
    let botanySeenSeeds = {}

    const botanySkip = reason => {
        botanySkipped++
        botanySkipReasons[reason] = (botanySkipReasons[reason] || 0) + 1
    }

    // Collected first and emitted afterwards. Two crops can name the same seed (a block-derived
    // crop and a hand-written one for the same plant), and two recipes sharing an id would
    // overwrite each other silently.
    const botanyCrops = []

    ;['botanypots:crop', 'botanypots:block_derived_crop'].forEach(cropType => {
        allthemods.forEachRecipe({ type: cropType }, rawRecipe => {
            // Every binding is declared here rather than inside the branches below: Rhino 2101
            // hoists a nested `const` to the enclosing function, so the second crop to take the
            // same branch dies on "redeclaration of var" and takes the whole callback with it.
            let recipeId = String(rawRecipe.getOrCreateId())
            let recipe = null
            let props = null
            let seedId = null
            let duration = 0
            let outputs = []
            let exact = true

            try {
                recipe = rawRecipe.getOriginalRecipe()
            } catch (e) {
                botanySkip('recipe would not deserialize')
                return
            }
            if (recipe === null) { botanySkip('recipe would not deserialize'); return }

            try {
                if (recipe instanceof $BlockDerivedCrop) {
                    props = recipe.getProperties().toBasic()
                } else if (recipe instanceof $BasicCrop) {
                    props = recipe.getBasicProperties()
                } else {
                    botanySkip('unknown crop class')
                    return
                }
            } catch (e) {
                botanySkip('could not resolve crop properties')
                return
            }

            try {
                seedId = String(props.input().getFirst().getId())
                duration = props.growTime()
            } catch (e) {
                botanySkip('no readable seed')
                return
            }
            if (!seedId || seedId === 'minecraft:air') { botanySkip('no readable seed'); return }
            if (!(duration > 0)) { botanySkip('no grow time'); return }

            // ATM9's two exclusions, kept verbatim: Mystical Agriculture is handled by the tier
            // lists further down, and these two produce duplicates of crops defined elsewhere.
            if (seedId.split(':')[0].includes('mystical')) { botanySkip('mysticalagriculture (covered by tier lists)'); return }
            if ((recipeId.split(':')[0].includes('botanytrees') && seedId.includes('croptopia'))
                || recipeId.split(':')[0].includes('supplementaries')) {
                botanySkip('known duplicate')
                return
            }

            if (botanySeenSeeds[seedId]) { botanySkip('duplicate seed'); return }

            try {
                props.drops().forEach(provider => {
                    if (provider instanceof $SimpleDropProvider) {
                        provider.drops().forEach(simple => {
                            let stack = simple.drop()
                            if (stack.isEmpty()) return
                            outputs.push({ id: String(stack.getId()), count: stack.getCount(), chance: simple.chance() })
                        })
                    } else {
                        // Loot-table and block-derived drops: a possible-item set with no weights.
                        // See the judgement call above.
                        let possible = []
                        provider.getDisplayItems().forEach(stack => {
                            if (!stack.isEmpty()) possible.push(stack)
                        })
                        if (possible.length === 0) return
                        exact = false
                        possible.forEach(stack => {
                            outputs.push({ id: String(stack.getId()), count: stack.getCount(), chance: 1 / possible.length })
                        })
                    }
                })
            } catch (e) {
                botanySkip('drops would not resolve')
                return
            }

            if (outputs.length === 0) { botanySkip('no drops'); return }

            botanySeenSeeds[seedId] = true
            botanyCrops.push({ seedId: seedId, duration: duration, outputs: outputs, exact: exact })
        })
    })

    botanyCrops.forEach(crop => {
        let affix = crop.seedId.split(':')
        let base = 'kubejs:gtceu/greenhouse/' + affix[0] + '/' + affix[1]
        let recipeBuilder = allthemods.recipes.gtceu.greenhouse(base)
        let recipeMAXBuilder = allthemods.recipes.gtceu.greenhouse(base + '_max')

        recipeBuilder.EUt(GTValues.VA[GTValues.MV])
            .notConsumableItem(crop.seedId)
            .inputFluids(Fluid.of('minecraft:water', 1000))
            .duration(Math.max(1, Math.floor(crop.duration / 4)))
            .circuit(0)
        recipeMAXBuilder.EUt(GTValues.VA[GTValues.UHV])
            .notConsumableItem(crop.seedId)
            .inputFluids(Fluid.of('minecraft:water', 1000 * 1310720))
            .duration(Math.max(1, Math.floor(crop.duration / 4 * 1024 / 15)))
            .circuit(5)

        crop.outputs.forEach(out => {
            if (out.chance >= 1) {
                recipeBuilder.itemOutputs(Item.of(out.id, out.count))
            } else {
                recipeBuilder.chancedOutput(Item.of(out.id, out.count), Math.max(1, Math.round(out.chance * 10000)))
            }
            recipeMAXBuilder.itemOutputs(Item.of(out.id, out.count * 1310720))
        })

        botanyCloned++
        if (crop.exact) { botanyExact++ } else { botanyApprox++ }
    })

    console.info(`[greenhouse] botanypots: ${botanyCloned} crops cloned (${botanyExact} with exact drop chances, ${botanyApprox} approximated from a loot table), ${botanySkipped} skipped`)
    if (botanySkipped > 0) {
        console.info(`[greenhouse] botanypots skipped: ${JSON.stringify(botanySkipReasons)}`)
    }

    //////////////// MA ////////////////

    //////////////// Tiers one and two ////////////////
    const infTier = ['air', 'earth', 'water', 'fire', 'inferium', 'stone', 'dirt', 'wood', 'ice']
    const prudTier = ['nature', 'dye', 'nether', 'coal', 'coral', 'honey', 'amethyst', 'pig', 'chicken', 'cow', 'sheep', 'squid', 'fish', 'slime',
        'turtle', 'rubber', 'silicon', 'sulfur', 'aluminum', 'saltpeter', 'apatite', 'grains_of_infinity', 'mystical_flower', 'limestone', 'menril']

    infTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600 / 2
        let scale = 163840

        // Guard at the CALL SITE, not inside grow(): the Item.of(out, scale) arguments below
        // are evaluated before the function is entered, so a check inside it never runs.
        if (!ghExists(inp) || !ghExists(out)) { ghMissing++; return }
        grow(id, inp, out, duration, 'minecraft:air', false)
        grow(id + '_boosted', inp, boostOut, duration, '4x mysticalagriculture:inferium_essence', true)
        grow_max(id + '_max', inp, Item.of(out, scale), duration * 1024 / 120, 'minecraft:air', false, scale)
        grow_max(id + '_max_boosted', inp, Item.of(out, scale * 4), duration * 1024 / 120, Item.of('mysticalagriculture:inferium_essence', 4 * scale), true, scale)
    })

    prudTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600 / 2
        let scale = 163840

        // Guard at the CALL SITE, not inside grow(): the Item.of(out, scale) arguments below
        // are evaluated before the function is entered, so a check inside it never runs.
        if (!ghExists(inp) || !ghExists(out)) { ghMissing++; return }
        grow(id, inp, out, duration, 'minecraft:air', false)
        grow(id + '_boosted', inp, boostOut, duration, '4x mysticalagriculture:prudentium_essence', true)
        grow_max(id + '_max', inp, Item.of(out, scale), duration * 1024 / 120, 'minecraft:air', false, scale)
        grow_max(id + '_max_boosted', inp, Item.of(out, scale * 4), duration * 1024 / 120, Item.of('mysticalagriculture:prudentium_essence', 4 * scale), true, scale)
    })

    //////////////// Tiers three and four ////////////////
    const tertTier = ['azure_silver', 'crimson_iron', 'iron', 'copper', 'nether_quartz', 'glowstone', 'redstone', 'obsidian', 'prismarine', 'zombie',
        'skeleton', 'creeper', 'spider', 'rabbit', 'tin', 'bronze', 'zinc', 'brass', 'silver', 'lead', 'graphite', 'blizz', 'blitz', 'basalz', 'copper_alloy',
        'redstone_alloy', 'conductive_alloy', 'manasteel', 'steeleaf', 'ironwood', 'sky_stone', 'certus_quartz', 'quartz_enriched_iron']
    const impTier = ['gold', 'lapis_lazuli', 'end', 'experience', 'blaze', 'ghast', 'enderman', 'steel', 'nickel', 'constantan', 'electrum', 'uranium', 'ruby',
        'sapphire', 'peridot', 'soulium', 'signalum', 'lumium', 'flux_infused_ingot', 'hop_graphite', 'soularium', 'dark_steel', 'pulsating_alloy',
        'energetic_alloy', 'elementium', 'osmium', 'fluorite', 'refined_glowstone', 'refined_obsidian', 'knightmetal', 'fiery_ingot', 'compressed_iron', 'fluix',
        'energized_steel', 'blazing_crystal']

    tertTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600
        let scale = 163840

        // Guard at the CALL SITE, not inside grow(): the Item.of(out, scale) arguments below
        // are evaluated before the function is entered, so a check inside it never runs.
        if (!ghExists(inp) || !ghExists(out)) { ghMissing++; return }
        grow(id, inp, out, duration, 'minecraft:air', false)
        grow(id + '_boosted', inp, boostOut, duration, '4x mysticalagriculture:tertium_essence', true)
        grow_max(id + '_max', inp, Item.of(out, scale), duration * 1024 / 120, 'minecraft:air', false, scale)
        grow_max(id + '_max_boosted', inp, Item.of(out, scale * 4), duration * 1024 / 120, Item.of('mysticalagriculture:tertium_essence', 4 * scale), true, scale)
    })

    impTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600
        let scale = 163840

        // Guard at the CALL SITE, not inside grow(): the Item.of(out, scale) arguments below
        // are evaluated before the function is entered, so a check inside it never runs.
        if (!ghExists(inp) || !ghExists(out)) { ghMissing++; return }
        grow(id, inp, out, duration, 'minecraft:air', false)
        grow(id + '_boosted', inp, boostOut, duration, '4x mysticalagriculture:imperium_essence', true)
        grow_max(id + '_max', inp, Item.of(out, scale), duration * 1024 / 120, 'minecraft:air', false, scale)
        grow_max(id + '_max_boosted', inp, Item.of(out, scale * 4), duration * 1024 / 120, Item.of('mysticalagriculture:imperium_essence', 4 * scale), true, scale)
    })

    //////////////// Tier five ////////////////
    const supTier = ['diamond', 'emerald', 'netherite', 'wither_skeleton', 'platinum', 'enderium', 'flux_infused_gem', 'vibrant_alloy', 'end_steel',
        'terrasteel', 'cyanite', 'niotic_crystal', 'spirited_crystal', 'uraninite', 'draconium']

    supTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600 * 3
        let scale = 163840

        // Guard at the CALL SITE, not inside grow(): the Item.of(out, scale) arguments below
        // are evaluated before the function is entered, so a check inside it never runs.
        if (!ghExists(inp) || !ghExists(out)) { ghMissing++; return }
        grow(id, inp, out, duration, 'minecraft:air', false)
        grow(id + '_boosted', inp, boostOut, duration, '4x mysticalagriculture:supremium_essence', true)
        grow_max(id + '_max', inp, Item.of(out, scale), duration * 1024 / 120, 'minecraft:air', false, scale)
        grow_max(id + '_max_boosted', inp, Item.of(out, scale * 4), duration * 1024 / 120, Item.of('mysticalagriculture:supremium_essence', 4 * scale), true, scale)
    })

    //////////////// Tier six ////////////////
    const cruxTier = ['nether_star', 'dragon_egg', 'nitro_crystal', 'allthemodium', 'unobtainium', 'vibranium', 'awakened_draconium']

    cruxTier.forEach((base) => {
        let id = 'kubejs:gtceu/greenhouse/mysticalagriculture/' + base
        let inp = 'mysticalagriculture:' + base + '_seeds'
        let crux = ''
        if (base.includes('allthemodium') || base.includes('unobtainium') || base.includes('vibranium')) {
            crux = 'kubejs:magical_soil'
        } else {
            crux = 'mysticalagradditions:' + base + '_crux'
        }
        let out = 'mysticalagriculture:' + base + '_essence'
        let boostOut = '4x mysticalagriculture:' + base + '_essence'
        let duration = 9600 * 9
        let scale = 163840
        // Crux tier builds recipes inline rather than through grow(), so it needs the same guard.
        if (!ghExists(inp) || !ghExists(crux)) { ghMissing++; return }


        allthemods.recipes.gtceu.greenhouse(id)
            .circuit(2)
            .notConsumableItem(inp)
            .notConsumableItem(crux)
            .itemInputs('4x mysticalagradditions:insanium_essence')
            .inputFluids(Fluid.of('minecraft:water', 1000))
            .itemOutputs(boostOut)
            .duration(duration / 2)
            .EUt(GTValues.VA[GTValues.MV])
        allthemods.recipes.gtceu.greenhouse(id + '_boosted')
            .circuit(1)
            .notConsumableItem(inp)
            .notConsumableItem(crux)
            .inputFluids(Fluid.of('minecraft:water', 1000))
            .itemOutputs(out)
            .duration(duration)
            .EUt(GTValues.VA[GTValues.MV])

        allthemods.recipes.gtceu.greenhouse(id + '_max')
            .circuit(4)
            .notConsumableItem(inp)
            .notConsumableItem(crux)
            .itemInputs(Item.of('mysticalagradditions:insanium_essence', 4 * scale))
            .inputFluids(Fluid.of('minecraft:water', 1000 * scale))
            .itemOutputs(Item.of('mysticalagriculture:' + base + '_essence', 4 * scale))
            .duration(duration / 2 * 1024 / 120)
            .EUt(GTValues.VA[GTValues.UHV])
        allthemods.recipes.gtceu.greenhouse(id + '_max_boosted')
            .circuit(3)
            .notConsumableItem(inp)
            .notConsumableItem(crux)
            .inputFluids(Fluid.of('minecraft:water', 1000 * scale))
            .itemOutputs(Item.of(out, scale))
            .duration(duration * 1024 / 120)
            .EUt(GTValues.VA[GTValues.UHV])
    })

    //////////////// GT Rubber trees ////////////////
    let scale = 163840
    grow('rubber_sapling', 'gtceu:rubber_sapling', ['32x gtceu:rubber_log', '8x gtceu:sticky_resin', '4x gtceu:rubber_sapling'], 9600.0 / 2, 'minecraft:air', false)
    grow('rubber_sapling_boosted', 'gtceu:rubber_sapling', ['64x gtceu:rubber_log', '16x gtceu:sticky_resin', '4x gtceu:rubber_sapling'], 9600.0 / 2, '4x gtceu:fertilizer', true)
    grow_max('rubber_sapling_max', 'gtceu:rubber_sapling', [Item.of('gtceu:rubber_log', 32 * scale), Item.of('gtceu:sticky_resin', 8 * scale), Item.of('gtceu:rubber_sapling', 2 * scale)], 9600.0 / 2 * 1024 / 120, 'minecraft:air', false, scale)
    grow_max('rubber_sapling_max_boosted', 'gtceu:rubber_sapling', [Item.of('gtceu:rubber_log', 64 * scale), Item.of('gtceu:sticky_resin', 16 * scale), Item.of('gtceu:rubber_sapling', 4 * scale)], 9600.0 / 2 * 1024 / 120, Item.of('gtceu:fertilizer', 4 * scale), true, scale)

    // The last line the greenhouse port prints. If the rubber-sapling recipes are missing in
    // game but this line appeared, the script ran to the end and the problem is elsewhere.
    console.info(`[greenhouse] done: ${ghMissing} entries skipped for missing items`)
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
