// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Original by AllTheMods Staff / community
// contributor; the license note from the original is kept in place below.
//
// A 117KB file with only three real changes, because it is a data table fed through three helper
// functions - process(), bathe() and sift() - and every port break lives in the helpers:
//
//   .chancedOutput(stack, chance, tierBoost) -> .chancedOutput(stack, chance)
//       GTCEu 8 dropped the tier-boost parameter from the item form. The bonus[] arrays are left
//       in the data and in the helper signature deliberately: they still document ATM9's intent,
//       and deleting 167 array literals to drop one argument is a diff nobody can review.
//   .EUt(ZPM) -> .EUt(GTValues.VA[GTValues.ZPM])
//       No GregTech version binds bare voltage names as globals.
//   #forge:ores/x, #forge:raw_materials/x -> #c:ores/x, #c:raw_materials/x   (334 sites)
//       The community tag namespace moved in 1.21. This is the dangerous one: the old names still
//       PARSE, so nothing throws - the recipe registers with an ingredient that matches nothing and
//       simply cannot be crafted. AllTheOres 3.2.0 ships zero forge: tags, confirming the layout.
//
// Whether each of the 226 distinct tags actually has members is a separate question from whether it
// parses. _id_check.js carried that list and reported the empty ones; it was retired to
// _id_check.js.old on 2026-08-31 once the port was finished. Rename it back if a pack update
// moves ids or empties a tag again - it reports every rename in one reload instead of one launch
// per rename, and its lists must be REGENERATED from the scripts, never hand-patched.
// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

ServerEvents.recipes(allthemods => {
    // machine controller in gtceu.js
    // function for macerate -> ore wash -> thermal centrifuge -> macerate again
    function process(id, inputs, fluids, outputs, chanced, chance, bonus, scale) {
        let recipe = allthemods.recipes.gtceu.ore_processing_plant('kubejs:' + id)
        recipe.itemInputs(inputs)
            .itemOutputs(outputs)
            .inputFluids(Fluid.of(fluids, 1000 * 128 * scale))
        for (let i = 0; i < chanced.length; i++) {
            recipe.chancedOutput(chanced[i], chance[i])
        }
        recipe.duration(800)
            .EUt(GTValues.VA[GTValues.ZPM])
    }
    // macerate -> chem bath -> thermal centrifuge -> macerate again
    function bathe(id, inputs, fluids, outputs, chanced, chance, bonus, scale) {
        let recipe = allthemods.recipes.gtceu.ore_bathing_plant('kubejs:' + id)
        recipe.itemInputs(inputs)
            .itemOutputs(outputs)
            .inputFluids(Fluid.of(fluids, 100 * 128 * scale))
        for (let i = 0; i < chanced.length; i++) {
            recipe.chancedOutput(chanced[i], chance[i])
        }
        recipe.duration(800)
            .EUt(GTValues.VA[GTValues.ZPM])
    }

    // macerate -> ore wash -> sifter
    function sift(id, inputs, fluids, outputs, chanced, chance, bonus, scale) {
        let recipe = allthemods.recipes.gtceu.ore_sifting_plant('kubejs:' + id)
        recipe.itemInputs(inputs)
            .itemOutputs(outputs)
            .inputFluids(Fluid.of(fluids, 1000 * 128 * scale))
        for (let i = 0; i < chanced.length; i++) {
            recipe.chancedOutput(chanced[i], chance[i])
        }
        recipe.duration(800)
            .EUt(GTValues.VA[GTValues.ZPM])
    }

    // aluminium
    process('aluminium', '128x #c:raw_materials/aluminum', 'minecraft:water', ['256x alltheores:aluminum_dust', '256x gtceu:stone_dust'], ['128x gtceu:bauxite_dust', '128x gtceu:stone_dust', '256x gtceu:bauxite_dust', '256x gtceu:bauxite_dust', '256x gtceu:ilmenite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('aluminium_silked', '128x #c:ores/aluminum', 'minecraft:water', ['512x alltheores:aluminum_dust', '640x gtceu:stone_dust'], ['128x gtceu:bauxite_dust', '512x gtceu:bauxite_dust', '512x gtceu:bauxite_dust', '512x gtceu:ilmenite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('aluminium', '128x #c:raw_materials/aluminum', 'gtceu:sodium_persulfate', ['256x alltheores:aluminum_dust', '256x gtceu:rutile_dust'], ['128x gtceu:bauxite_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:bauxite_dust', '256x gtceu:ilmenite_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('aluminium_silked', '128x #c:ores/aluminum', 'gtceu:sodium_persulfate', ['512x alltheores:aluminum_dust', '512x gtceu:rutile_dust', '128x gtceu:stone_dust'], ['128x gtceu:bauxite_dust', '512x gtceu:stone_dust', '512x gtceu:bauxite_dust', '512x gtceu:ilmenite_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // beryllium
    process('beryllium', '128x #c:raw_materials/beryllium', 'minecraft:water', ['256x gtceu:beryllium_dust', '256x gtceu:stone_dust'], ['128x minecraft:emerald', '128x gtceu:stone_dust', '256x gtceu:emerald_dust', '256x gtceu:emerald_dust', '256x gtceu:thorium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('beryllium_silked', '128x #c:ores/beryllium', 'minecraft:water', ['512x gtceu:beryllium_dust', '640x gtceu:stone_dust'], ['128x minecraft:emerald', '512x gtceu:emerald_dust', '512x gtceu:emerald_dust', '512x gtceu:thorium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // cobalt
    process('cobalt', '128x #c:raw_materials/cobalt', 'minecraft:water', ['256x gtceu:cobalt_dust', '256x gtceu:stone_dust'], ['128x gtceu:cobalt_oxide_dust', '128x gtceu:stone_dust', '256x gtceu:cobalt_oxide_dust', '256x gtceu:cobaltite_dust', '256x gtceu:cobaltite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('cobalt_silked', '128x #c:ores/cobalt', 'minecraft:water', ['512x gtceu:cobalt_dust', '640x gtceu:stone_dust'], ['128x gtceu:cobalt_oxide_dust', '512x gtceu:cobalt_oxide_dust', '512x gtceu:cobaltite_dust', '512x gtceu:cobaltite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('cobalt', '128x #c:raw_materials/cobalt', 'gtceu:sodium_persulfate', ['256x gtceu:cobalt_dust', '256x gtceu:cobaltite_dust'], ['128x gtceu:cobalt_oxide_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:cobaltite_dust', '256x gtceu:cobaltite_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('cobalt_silked', '128x #c:ores/cobalt', 'gtceu:sodium_persulfate', ['512x gtceu:cobalt_dust', '512x gtceu:cobaltite_dust', '128x gtceu:stone_dust'], ['128x gtceu:cobalt_oxide_dust', '512x gtceu:stone_dust', '512x gtceu:cobaltite_dust', '512x gtceu:cobaltite_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // copper
    process('copper', '128x #c:raw_materials/copper', 'minecraft:water', ['256x alltheores:copper_dust', '256x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '128x gtceu:stone_dust', '256x gtceu:cobalt_dust', '256x alltheores:gold_dust', '256x alltheores:nickel_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('copper_silked', '128x #c:ores/copper', 'minecraft:water', ['512x alltheores:copper_dust', '640x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '512x gtceu:cobalt_dust', '512x alltheores:gold_dust', '512x alltheores:nickel_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('copper', '128x #c:raw_materials/copper', 'gtceu:mercury', ['256x alltheores:copper_dust', '256x alltheores:gold_dust'], ['128x gtceu:cobalt_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:gold_dust', '256x alltheores:nickel_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('copper_silked', '128x #c:ores/copper', 'gtceu:mercury', ['512x alltheores:copper_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '512x gtceu:stone_dust', '512x alltheores:gold_dust', '512x alltheores:nickel_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // gold
    process('gold', '128x #c:raw_materials/gold', 'minecraft:water', ['256x alltheores:gold_dust', '256x gtceu:stone_dust'], ['128x alltheores:copper_dust', '128x gtceu:stone_dust', '256x alltheores:copper_dust', '256x alltheores:nickel_dust', '256x alltheores:silver_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('gold_silked', '128x #c:ores/gold', 'minecraft:water', ['512x alltheores:gold_dust', '640x gtceu:stone_dust'], ['128x alltheores:copper_dust', '512x alltheores:copper_dust', '512x alltheores:nickel_dust', '512x alltheores:silver_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('gold', '128x #c:raw_materials/gold', 'gtceu:mercury', ['256x alltheores:gold_dust', '256x alltheores:silver_dust'], ['128x alltheores:copper_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x alltheores:silver_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('gold_silked', '128x #c:ores/gold', 'gtceu:mercury', ['512x alltheores:gold_dust', '512x alltheores:silver_dust', '128x gtceu:stone_dust'], ['128x alltheores:copper_dust', '512x gtceu:stone_dust', '512x alltheores:nickel_dust', '512x alltheores:silver_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // iron
    process('iron', '128x #c:raw_materials/iron', 'minecraft:water', ['256x alltheores:iron_dust', '256x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x alltheores:tin_dust', '256x alltheores:tin_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('iron_silked', '128x #c:ores/iron', 'minecraft:water', ['512x alltheores:iron_dust', '640x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x alltheores:tin_dust', '512x alltheores:tin_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('iron', '128x #c:raw_materials/iron', 'gtceu:sodium_persulfate', ['256x alltheores:iron_dust', '256x alltheores:gold_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:tin_dust', '256x alltheores:tin_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('iron_silked', '128x #c:ores/iron', 'gtceu:sodium_persulfate', ['512x alltheores:iron_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x gtceu:stone_dust', '512x alltheores:tin_dust', '512x alltheores:tin_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // lead
    process('lead', '128x #c:raw_materials/lead', 'minecraft:water', ['256x alltheores:lead_dust', '256x gtceu:stone_dust'], ['128x alltheores:silver_dust', '128x gtceu:stone_dust', '256x alltheores:silver_dust', '256x gtceu:sulfur_dust', '256x gtceu:sulfur_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('lead_silked', '128x #c:ores/lead', 'minecraft:water', ['512x alltheores:lead_dust', '640x gtceu:stone_dust'], ['128x alltheores:silver_dust', '512x alltheores:silver_dust', '512x gtceu:sulfur_dust', '512x gtceu:sulfur_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // lithium
    process('lithium', '128x #c:raw_materials/lithium', 'minecraft:water', ['256x gtceu:lithium_dust', '256x gtceu:stone_dust'], ['128x gtceu:lithium_dust', '128x gtceu:stone_dust', '256x gtceu:lithium_dust', '256x gtceu:lithium_dust', '256x gtceu:lithium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('lithium_silked', '128x #c:ores/lithium', 'minecraft:water', ['512x gtceu:lithium_dust', '640x gtceu:stone_dust'], ['128x gtceu:lithium_dust', '512x gtceu:lithium_dust', '512x gtceu:lithium_dust', '512x gtceu:lithium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // molybdenum
    process('molybdenum', '128x #c:raw_materials/molybdenum', 'minecraft:water', ['256x gtceu:molybdenum_dust', '256x gtceu:stone_dust'], ['128x gtceu:molybdenum_dust', '128x gtceu:stone_dust', '256x gtceu:molybdenum_dust', '256x gtceu:molybdenum_dust', '256x gtceu:molybdenum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('molybdenum_silked', '128x #c:ores/molybdenum', 'minecraft:water', ['512x gtceu:molybdenum_dust', '640x gtceu:stone_dust'], ['128x gtceu:molybdenum_dust', '512x gtceu:molybdenum_dust', '512x gtceu:molybdenum_dust', '512x gtceu:molybdenum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // neodymium
    process('neodymium', '128x #c:raw_materials/neodymium', 'minecraft:water', ['256x gtceu:neodymium_dust', '256x gtceu:stone_dust'], ['128x gtceu:rare_earth_dust', '128x gtceu:stone_dust', '256x gtceu:rare_earth_dust', '256x gtceu:rare_earth_dust', '256x gtceu:rare_earth_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('neodymium_silked', '128x #c:ores/neodymium', 'minecraft:water', ['512x gtceu:neodymium_dust', '640x gtceu:stone_dust'], ['128x gtceu:rare_earth_dust', '512x gtceu:rare_earth_dust', '512x gtceu:rare_earth_dust', '512x gtceu:rare_earth_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // nickel
    process('nickel', '128x #c:raw_materials/nickel', 'minecraft:water', ['256x alltheores:nickel_dust', '256x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '128x gtceu:stone_dust', '256x gtceu:cobalt_dust', '256x alltheores:iron_dust', '256x alltheores:platinum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('nickel_silked', '128x #c:ores/nickel', 'minecraft:water', ['512x alltheores:nickel_dust', '640x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '512x gtceu:cobalt_dust', '512x alltheores:iron_dust', '512x alltheores:platinum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('nickel', '128x #c:raw_materials/nickel', 'gtceu:mercury', ['256x alltheores:nickel_dust', '256x alltheores:platinum_dust'], ['128x gtceu:cobalt_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:iron_dust', '256x alltheores:platinum_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('nickel_silked', '128x #c:ores/nickel', 'gtceu:mercury', ['512x alltheores:nickel_dust', '512x alltheores:platinum_dust', '128x gtceu:stone_dust'], ['128x gtceu:cobalt_dust', '512x gtceu:stone_dust', '512x alltheores:iron_dust', '512x alltheores:platinum_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // palladium
    process('palladium', '128x #c:raw_materials/palladium', 'minecraft:water', ['256x gtceu:palladium_dust', '256x gtceu:stone_dust'], ['128x gtceu:palladium_dust', '128x gtceu:stone_dust', '256x gtceu:palladium_dust', '256x gtceu:palladium_dust', '256x gtceu:palladium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('palladium_silked', '128x #c:ores/palladium', 'minecraft:water', ['512x gtceu:palladium_dust', '640x gtceu:stone_dust'], ['128x gtceu:palladium_dust', '512x gtceu:palladium_dust', '512x gtceu:palladium_dust', '512x gtceu:palladium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // platinum
    process('platinum', '128x #c:raw_materials/platinum', 'minecraft:water', ['256x alltheores:platinum_dust', '256x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x alltheores:nickel_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('platinum_silked', '128x #c:ores/platinum', 'minecraft:water', ['512x alltheores:platinum_dust', '640x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x gtceu:cobalt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('platinum', '128x #c:raw_materials/platinum', 'gtceu:mercury', ['256x alltheores:platinum_dust', '256x gtceu:palladium_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('platinum_silked', '128x #c:ores/platinum', 'gtceu:mercury', ['512x alltheores:platinum_dust', '512x gtceu:palladium_dust', '128x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x gtceu:stone_dust', '512x alltheores:nickel_dust', '512x gtceu:cobalt_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // plutonium
    process('plutonium', '128x #c:raw_materials/plutonium_239', 'minecraft:water', ['256x gtceu:plutonium_239_dust', '256x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '128x gtceu:stone_dust', '256x gtceu:uraninite_dust', '256x alltheores:lead_dust', '256x gtceu:uraninite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('plutonium_silked', '128x #c:ores/plutonium_239', 'minecraft:water', ['512x gtceu:plutonium_239_dust', '640x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '512x gtceu:uraninite_dust', '512x alltheores:lead_dust', '512x gtceu:uraninite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // silver
    process('silver', '128x #c:raw_materials/silver', 'minecraft:water', ['256x alltheores:silver_dust', '256x gtceu:stone_dust'], ['128x alltheores:lead_dust', '128x gtceu:stone_dust', '256x alltheores:lead_dust', '256x gtceu:sulfur_dust', '256x gtceu:sulfur_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('silver_silked', '128x #c:ores/silver', 'minecraft:water', ['512x alltheores:silver_dust', '640x gtceu:stone_dust'], ['128x alltheores:lead_dust', '512x alltheores:lead_dust', '512x gtceu:sulfur_dust', '512x gtceu:sulfur_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('silver', '128x #c:raw_materials/silver', 'gtceu:mercury', ['256x alltheores:silver_dust', '256x alltheores:gold_dust'], ['128x alltheores:lead_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:sulfur_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('silver_silked', '128x #c:ores/silver', 'gtceu:mercury', ['512x alltheores:silver_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x alltheores:lead_dust', '512x gtceu:stone_dust', '512x gtceu:sulfur_dust', '512x gtceu:sulfur_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // sulfur
    process('sulfur', '128x #c:raw_materials/sulfur', 'minecraft:water', ['256x gtceu:sulfur_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:sulfur_dust', '256x gtceu:sulfur_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('sulfur_silked', '128x #c:ores/sulfur', 'minecraft:water', ['512x gtceu:sulfur_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:sulfur_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // thorium
    process('thorium', '128x #c:raw_materials/thorium', 'minecraft:water', ['256x gtceu:thorium_dust', '256x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '128x gtceu:stone_dust', '256x gtceu:uraninite_dust', '256x alltheores:lead_dust', '256x alltheores:lead_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('thorium_silked', '128x #c:ores/thorium', 'minecraft:water', ['512x gtceu:thorium_dust', '640x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '512x gtceu:uraninite_dust', '512x alltheores:lead_dust', '512x alltheores:lead_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // tin
    process('tin', '128x #c:raw_materials/tin', 'minecraft:water', ['256x alltheores:tin_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x alltheores:zinc_dust', '256x alltheores:zinc_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('tin_silked', '128x #c:ores/tin', 'minecraft:water', ['512x alltheores:tin_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x alltheores:zinc_dust', '512x alltheores:zinc_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('tin', '128x #c:raw_materials/tin', 'gtceu:sodium_persulfate', ['256x alltheores:tin_dust', '256x alltheores:zinc_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:zinc_dust', '256x alltheores:zinc_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('tin_silked', '128x #c:ores/tin', 'gtceu:sodium_persulfate', ['512x alltheores:tin_dust', '512x alltheores:zinc_dust', '128x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x gtceu:stone_dust', '512x alltheores:zinc_dust', '512x alltheores:zinc_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // naquadah
    process('naquadah', '128x #c:raw_materials/naquadah', 'minecraft:water', ['256x gtceu:naquadah_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:barite_dust', '256x gtceu:enriched_naquadah_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('naquadah_silked', '128x #c:ores/naquadah', 'minecraft:water', ['512x gtceu:naquadah_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:barite_dust', '512x gtceu:enriched_naquadah_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // trinium
    process('trinium', '128x #c:raw_materials/trinium', 'minecraft:water', ['256x gtceu:trinium_dust', '256x gtceu:stone_dust'], ['128x gtceu:trinium_dust', '128x gtceu:stone_dust', '256x gtceu:trinium_dust', '256x gtceu:trinium_dust', '256x gtceu:trinium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('trinium_silked', '128x #c:ores/trinium', 'minecraft:water', ['512x gtceu:trinium_dust', '640x gtceu:stone_dust'], ['128x gtceu:trinium_dust', '512x gtceu:trinium_dust', '512x gtceu:trinium_dust', '512x gtceu:trinium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // almandine
    process('almandine', '128x #c:raw_materials/almandine', 'minecraft:water', ['768x gtceu:almandine_dust', '768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x alltheores:aluminum_dust', '768x alltheores:aluminum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('almandine_silked', '128x #c:ores/almandine', 'minecraft:water', ['1536x gtceu:almandine_dust', '1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x alltheores:aluminum_dust', '1536x alltheores:aluminum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    sift('almandine', '128x #c:raw_materials/almandine', 'minecraft:water', ['768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x gtceu:exquisite_almandine_gem', '768x gtceu:flawless_almandine_gem', '768x gtceu:almandine_gem', '768x gtceu:pure_almandine_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 3)
    sift('almandine_silked', '128x #c:ores/almandine', 'minecraft:water', ['1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x gtceu:exquisite_almandine_gem', '1536x gtceu:flawless_almandine_gem', '1536x gtceu:almandine_gem', '1536x gtceu:pure_almandine_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)

    // asbestos
    process('asbestos', '128x #c:raw_materials/asbestos', 'minecraft:water', ['768x gtceu:asbestos_dust', '768x gtceu:stone_dust'], ['128x gtceu:diatomite_dust', '128x gtceu:stone_dust', '768x gtceu:diatomite_dust', '768x gtceu:silicon_dust', '768x gtceu:magnesium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('asbestos_silked', '128x #c:ores/asbestos', 'minecraft:water', ['1536x gtceu:asbestos_dust', '1664x gtceu:stone_dust'], ['128x gtceu:diatomite_dust', '1536x gtceu:diatomite_dust', '1536x gtceu:silicon_dust', '1536x gtceu:magnesium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)

    // hematite
    process('hematite', '128x #c:raw_materials/hematite', 'minecraft:water', ['256x gtceu:hematite_dust', '256x gtceu:stone_dust'], ['128x gtceu:magnetite_dust', '128x gtceu:stone_dust', '256x gtceu:magnetite_dust', '256x gtceu:calcium_dust', '256x gtceu:magnesium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('hematite_silked', '128x #c:ores/hematite', 'minecraft:water', ['512x gtceu:hematite_dust', '640x gtceu:stone_dust'], ['128x gtceu:magnetite_dust', '512x gtceu:magnetite_dust', '512x gtceu:calcium_dust', '512x gtceu:magnesium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // blue topaz
    process('blue_topaz', '128x #c:raw_materials/blue_topaz', 'minecraft:water', ['512x gtceu:blue_topaz_dust', '512x gtceu:stone_dust'], ['128x gtceu:topaz_gem', '128x gtceu:stone_dust', '512x gtceu:topaz_dust', '512x gtceu:topaz_dust', '512x gtceu:topaz_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('blue_topaz_silked', '128x #c:ores/blue_topaz', 'minecraft:water', ['1024x gtceu:blue_topaz_dust', '1152x gtceu:stone_dust'], ['128x gtceu:topaz_gem', '1024x gtceu:topaz_dust', '1024x gtceu:topaz_dust', '1024x gtceu:topaz_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('blue_topaz', '128x #c:raw_materials/blue_topaz', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:topaz_gem', '128x gtceu:stone_dust', '512x gtceu:topaz_dust', '512x gtceu:exquisite_blue_topaz_gem', '512x gtceu:flawless_blue_topaz_gem', '512x gtceu:blue_topaz_gem', '512x gtceu:pure_blue_topaz_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('blue_topaz_silked', '128x #c:ores/blue_topaz', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:topaz_gem', '1024x gtceu:topaz_dust', '1024x gtceu:exquisite_blue_topaz_gem', '1024x gtceu:flawless_blue_topaz_gem', '1024x gtceu:blue_topaz_gem', '1024x gtceu:pure_blue_topaz_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)

    // goethite
    process('goethite', '128x #c:raw_materials/goethite', 'minecraft:water', ['256x gtceu:goethite_dust', '256x gtceu:stone_dust'], ['128x gtceu:malachite_gem', '128x gtceu:stone_dust', '256x gtceu:malachite_dust', '256x gtceu:yellow_limonite_dust', '256x gtceu:yellow_limonite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('goethite_silked', '128x #c:ores/goethite', 'minecraft:water', ['512x gtceu:goethite_dust', '640x gtceu:stone_dust'], ['128x gtceu:malachite_gem', '512x gtceu:malachite_dust', '512x gtceu:yellow_limonite_dust', '512x gtceu:yellow_limonite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // calcite
    process('calcite', '128x #c:raw_materials/calcite', 'minecraft:water', ['256x gtceu:calcite_dust', '256x gtceu:stone_dust'], ['128x gtceu:calcium_dust', '128x gtceu:stone_dust', '256x gtceu:calcium_dust', '256x gtceu:calcium_dust', '256x gtceu:sodalite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('calcite_silked', '128x #c:ores/calcite', 'minecraft:water', ['512x gtceu:calcite_dust', '640x gtceu:stone_dust'], ['128x gtceu:calcium_dust', '512x gtceu:calcium_dust', '512x gtceu:calcium_dust', '512x gtceu:sodalite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // cassiterite
    process('cassiterite', '128x #c:raw_materials/cassiterite', 'minecraft:water', ['512x gtceu:cassiterite_dust', '512x gtceu:stone_dust'], ['128x alltheores:tin_dust', '128x gtceu:stone_dust', '512x alltheores:tin_dust', '512x gtceu:bismuth_dust', '512x gtceu:bismuth_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('cassiterite_silked', '128x #c:ores/cassiterite', 'minecraft:water', ['1024x gtceu:cassiterite_dust', '1152x gtceu:stone_dust'], ['128x alltheores:tin_dust', '1024x alltheores:tin_dust', '1024x gtceu:bismuth_dust', '1024x gtceu:bismuth_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)

    // cassiterite sand
    process('cassiterite_sand', '128x #c:raw_materials/cassiterite_sand', 'minecraft:water', ['512x gtceu:cassiterite_sand_dust', '512x gtceu:stone_dust'], ['128x alltheores:tin_dust', '128x gtceu:stone_dust', '512x alltheores:tin_dust', '512x alltheores:tin_dust', '512x alltheores:tin_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('cassiterite_sand_silked', '128x #c:ores/cassiterite_sand', 'minecraft:water', ['1024x gtceu:cassiterite_sand_dust', '1152x gtceu:stone_dust'], ['128x alltheores:tin_dust', '1024x alltheores:tin_dust', '1024x alltheores:tin_dust', '1024x alltheores:tin_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)

    // chalcopyrite
    process('chalcopyrite', '128x #c:raw_materials/chalcopyrite', 'minecraft:water', ['256x gtceu:chalcopyrite_dust', '256x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '128x gtceu:stone_dust', '256x gtceu:pyrite_dust', '256x gtceu:cobalt_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('chalcopyrite_silked', '128x #c:ores/chalcopyrite', 'minecraft:water', ['512x gtceu:chalcopyrite_dust', '640x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '512x gtceu:pyrite_dust', '512x gtceu:cobalt_dust', '512x gtceu:cadmium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('chalcopyrite', '128x #c:raw_materials/chalcopyrite', 'gtceu:mercury', ['256x gtceu:chalcopyrite_dust', '256x alltheores:gold_dust'], ['128x gtceu:pyrite_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:cobalt_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('chalcopyrite_silked', '128x #c:ores/chalcopyrite', 'gtceu:mercury', ['512x gtceu:chalcopyrite_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '512x gtceu:stone_dust', '512x gtceu:cobalt_dust', '512x gtceu:cadmium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // chromite
    process('chromite', '128x #c:raw_materials/chromite', 'minecraft:water', ['256x gtceu:chromite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x gtceu:magnesium_dust', '256x gtceu:chromium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('chromite_silked', '128x #c:ores/chromite', 'minecraft:water', ['512x gtceu:chromite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x gtceu:magnesium_dust', '512x gtceu:chromium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // cinnabar
    process('cinnabar', '128x #c:raw_materials/cinnabar', 'minecraft:water', ['256x gtceu:cinnabar_dust', '256x gtceu:stone_dust'], ['128x minecraft:redstone', '128x gtceu:stone_dust', '256x minecraft:redstone', '256x gtceu:sulfur_dust', '256x minecraft:glowstone_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('cinnabar_silked', '128x #c:ores/cinnabar', 'minecraft:water', ['512x gtceu:cinnabar_dust', '640x gtceu:stone_dust'], ['128x minecraft:redstone', '512x minecraft:redstone', '512x gtceu:sulfur_dust', '512x minecraft:glowstone_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('cinnabar', '128x #c:raw_materials/cinnabar', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x minecraft:redstone', '128x gtceu:stone_dust', '256x minecraft:redstone', '256x gtceu:exquisite_cinnabar_gem', '256x gtceu:flawless_cinnabar_gem', '256x gtceu:cinnabar_gem', '256x gtceu:pure_cinnabar_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('cinnabar_silked', '128x #c:ores/cinnabar', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x minecraft:redstone', '512x minecraft:redstone', '512x gtceu:exquisite_cinnabar_gem', '512x gtceu:flawless_cinnabar_gem', '512x gtceu:cinnabar_gem', '512x gtceu:pure_cinnabar_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // coal
    process('coal', '128x #c:raw_materials/coal', 'minecraft:water', ['512x gtceu:coal_dust', '512x gtceu:stone_dust'], ['128x minecraft:coal', '128x gtceu:stone_dust', '512x gtceu:coal_dust', '512x gtceu:coal_dust', '512x gtceu:thorium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('coal_silked', '128x #c:ores/coal', 'minecraft:water', ['512x gtceu:coal_dust', '1152x gtceu:stone_dust'], ['128x minecraft:coal', '1024x gtceu:coal_dust', '1024x gtceu:coal_dust', '1024x gtceu:thorium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('coal', '128x #c:raw_materials/coal', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x minecraft:coal', '128x gtceu:stone_dust', '512x gtceu:coal_dust', '512x gtceu:exquisite_coal_gem', '512x gtceu:flawless_coal_gem', '512x minecraft:coal', '512x gtceu:pure_coal_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('coal_silked', '128x #c:ores/coal', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x minecraft:coal', '1024x gtceu:coal_dust', '1024x gtceu:exquisite_coal_gem', '1024x gtceu:flawless_coal_gem', '1024x minecraft:coal', '1024x gtceu:pure_coal_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)

    // cobaltite
    process('cobaltite', '128x #c:raw_materials/cobaltite', 'minecraft:water', ['256x gtceu:cobaltite_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:cobalt_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('cobaltite_silked', '128x #c:ores/cobaltite', 'minecraft:water', ['512x gtceu:cobaltite_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:cobalt_dust', '512x gtceu:cobalt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('cobaltite', '128x #c:raw_materials/cobaltite', 'gtceu:sodium_persulfate', ['256x gtceu:cobaltite_dust', '256x gtceu:cobalt_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:cobalt_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('cobaltite_silked', '128x #c:ores/cobaltite', 'gtceu:sodium_persulfate', ['512x gtceu:cobaltite_dust', '512x gtceu:cobalt_dust', '128x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:stone_dust', '512x gtceu:cobalt_dust', '512x gtceu:cobalt_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // sheldonite (cooperite)
    process('cooperite', '128x #c:raw_materials/cooperite', 'minecraft:water', ['256x gtceu:cooperite_dust', '256x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x alltheores:nickel_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('cooperite_silked', '128x #c:ores/cooperite', 'minecraft:water', ['512x gtceu:cooperite_dust', '640x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x gtceu:cobalt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('cooperite', '128x #c:raw_materials/cooperite', 'gtceu:mercury', ['256x gtceu:cooperite_dust', '256x gtceu:palladium_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('cooperite_silked', '128x #c:ores/cooperite', 'gtceu:mercury', ['512x gtceu:cooperite_dust', '512x gtceu:palladium_dust', '128x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x gtceu:stone_dust', '512x alltheores:nickel_dust', '512x gtceu:cobalt_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // diamond
    process('diamond', '128x #c:raw_materials/diamond', 'minecraft:water', ['256x gtceu:diamond_dust', '256x gtceu:stone_dust'], ['128x gtceu:graphite_dust', '128x gtceu:stone_dust', '256x gtceu:graphite_dust', '256x gtceu:graphite_dust', '256x gtceu:graphite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('diamond_silked', '128x #c:ores/diamond', 'minecraft:water', ['512x gtceu:diamond_dust', '640x gtceu:stone_dust'], ['128x gtceu:graphite_dust', '512x gtceu:graphite_dust', '512x gtceu:graphite_dust', '512x gtceu:graphite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('diamond', '128x #c:raw_materials/diamond', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:graphite_dust', '128x gtceu:stone_dust', '256x gtceu:graphite_dust', '256x gtceu:exquisite_diamond_gem', '256x gtceu:flawless_diamond_gem', '256x minecraft:diamond', '256x gtceu:pure_diamond_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('diamond_silked', '128x #c:ores/diamond', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:graphite_dust', '512x gtceu:graphite_dust', '512x gtceu:exquisite_diamond_gem', '512x gtceu:flawless_diamond_gem', '512x minecraft:diamond', '512x gtceu:pure_diamond_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // emerald
    process('emerald', '128x #c:raw_materials/emerald', 'minecraft:water', ['512x gtceu:emerald_dust', '512x gtceu:stone_dust'], ['128x gtceu:beryllium_dust', '128x gtceu:stone_dust', '512x gtceu:beryllium_dust', '512x alltheores:aluminum_dust', '512x alltheores:aluminum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('emerald_silked', '128x #c:ores/emerald', 'minecraft:water', ['1024x gtceu:emerald_dust', '1152x gtceu:stone_dust'], ['128x gtceu:beryllium_dust', '1024x gtceu:beryllium_dust', '1024x alltheores:aluminum_dust', '1024x alltheores:aluminum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('emerald', '128x #c:raw_materials/emerald', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:beryllium_dust', '128x gtceu:stone_dust', '512x gtceu:beryllium_dust', '512x gtceu:exquisite_emerald_gem', '512x gtceu:flawless_emerald_gem', '512x minecraft:emerald', '512x gtceu:pure_emerald_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('emerald_silked', '128x #c:ores/emerald', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:beryllium_dust', '1024x gtceu:beryllium_dust', '1024x gtceu:exquisite_emerald_gem', '1024x gtceu:flawless_emerald_gem', '1024x minecraft:emerald', '1024x gtceu:pure_emerald_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)

    // galena
    process('galena', '128x #c:raw_materials/galena', 'minecraft:water', ['256x gtceu:galena_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x alltheores:silver_dust', '256x alltheores:silver_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('galena_silked', '128x #c:ores/galena', 'minecraft:water', ['512x gtceu:galena_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x alltheores:silver_dust', '512x alltheores:silver_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('galena', '128x #c:raw_materials/galena', 'gtceu:mercury', ['256x gtceu:galena_dust', '256x alltheores:silver_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:silver_dust', '256x alltheores:silver_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('galena_silked', '128x #c:ores/galena', 'gtceu:mercury', ['512x gtceu:galena_dust', '512x alltheores:silver_dust', '128x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:stone_dust', '512x alltheores:silver_dust', '512x alltheores:silver_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // garnierite
    process('garnierite', '128x #c:raw_materials/garnierite', 'minecraft:water', ['256x gtceu:garnierite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x alltheores:nickel_dust', '256x alltheores:nickel_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('garnierite_silked', '128x #c:ores/garnierite', 'minecraft:water', ['512x gtceu:garnierite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x alltheores:nickel_dust', '512x alltheores:nickel_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // green sapphire
    process('green_sapphire', '128x #c:raw_materials/green_sapphire', 'minecraft:water', ['256x gtceu:green_sapphire_dust', '256x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '256x alltheores:aluminum_dust', '256x gtceu:sapphire_dust', '256x gtceu:sapphire_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('green_sapphire_silked', '128x #c:ores/green_sapphire', 'minecraft:water', ['512x gtceu:green_sapphire_dust', '640x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '512x alltheores:aluminum_dust', '512x gtceu:sapphire_dust', '512x gtceu:sapphire_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('green_sapphire', '128x #c:raw_materials/green_sapphire', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '256x alltheores:aluminum_dust', '256x gtceu:exquisite_green_sapphire_gem', '256x gtceu:flawless_green_sapphire_gem', '256x gtceu:green_sapphire_gem', '256x gtceu:pure_green_sapphire_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('green_sapphire_silked', '128x #c:ores/green_sapphire', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '512x alltheores:aluminum_dust', '512x gtceu:exquisite_green_sapphire_gem', '512x gtceu:flawless_green_sapphire_gem', '512x gtceu:green_sapphire_gem', '512x gtceu:pure_green_sapphire_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // grossular
    process('grossular', '128x #c:raw_materials/grossular', 'minecraft:water', ['768x gtceu:green_sapphire_dust', '768x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:yellow_garnet_dust', '768x gtceu:calcium_dust', '768x gtceu:calcium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('grossular_silked', '128x #c:ores/grossular', 'minecraft:water', ['1536x gtceu:green_sapphire_dust', '1664x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '1536x gtceu:yellow_garnet_dust', '1536x gtceu:calcium_dust', '1536x gtceu:calcium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    sift('grossular', '128x #c:raw_materials/grossular', 'minecraft:water', ['768x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:yellow_garnet_dust', '768x gtceu:exquisite_grossular_gem', '768x gtceu:flawless_grossular_gem', '768x gtceu:grossular_gem', '768x gtceu:pure_grossular_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 3)
    sift('grossular_silked', '128x #c:ores/grossular', 'minecraft:water', ['1664x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '1536x gtceu:yellow_garnet_dust', '1536x gtceu:exquisite_grossular_gem', '1536x gtceu:flawless_grossular_gem', '1536x gtceu:grossular_gem', '1536x gtceu:pure_grossular_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)

    // ilmenite
    process('ilmenite', '128x #c:raw_materials/ilmenite', 'minecraft:water', ['256x gtceu:ilmenite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x gtceu:rutile_dust', '256x gtceu:rutile_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('ilmenite_silked', '128x #c:ores/ilmenite', 'minecraft:water', ['512x gtceu:ilmenite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x gtceu:rutile_dust', '512x gtceu:rutile_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // bauxite
    process('bauxite', '128x #c:raw_materials/bauxite', 'minecraft:water', ['256x gtceu:bauxite_dust', '256x gtceu:stone_dust'], ['128x gtceu:grossular_gem', '128x gtceu:stone_dust', '256x gtceu:grossular_dust', '256x gtceu:rutile_dust', '256x gtceu:gallium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('bauxite_silked', '128x #c:ores/bauxite', 'minecraft:water', ['512x gtceu:bauxite_dust', '640x gtceu:stone_dust'], ['128x gtceu:grossular_gem', '512x gtceu:grossular_dust', '512x gtceu:rutile_dust', '512x gtceu:gallium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('bauxite', '128x #c:raw_materials/bauxite', 'gtceu:sodium_persulfate', ['256x gtceu:bauxite_dust', '256x gtceu:gallium_dust'], ['128x gtceu:grossular_gem', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:rutile_dust', '256x gtceu:gallium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('bauxite_silked', '128x #c:ores/bauxite', 'gtceu:sodium_persulfate', ['512x gtceu:bauxite_dust', '512x gtceu:gallium_dust', '128x gtceu:stone_dust'], ['128x gtceu:grossular_gem', '512x gtceu:stone_dust', '512x gtceu:rutile_dust', '512x gtceu:gallium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // lazurite
    process('lazurite', '128x #c:raw_materials/lazurite', 'minecraft:water', ['1536x gtceu:lazurite_dust', '1536x gtceu:stone_dust'], ['128x gtceu:sodalite_gem', '128x gtceu:stone_dust', '1536x gtceu:sodalite_dust', '6144x gtceu:lapis_dust', '1536x gtceu:lapis_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 6)
    process('lazurite_silked', '128x #c:ores/lazurite', 'minecraft:water', ['3072x gtceu:lazurite_dust', '3200x gtceu:stone_dust'], ['128x gtceu:sodalite_gem', '3072x gtceu:sodalite_dust', '12288x gtceu:lapis_dust', '3072x gtceu:lapis_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 12)
    sift('lazurite', '128x #c:raw_materials/lazurite', 'minecraft:water', ['1536x gtceu:stone_dust'], ['128x gtceu:sodalite_gem', '128x gtceu:stone_dust', '1536x gtceu:sodalite_dust', '1536x gtceu:exquisite_lazurite_gem', '1536x gtceu:flawless_lazurite_gem', '1536x gtceu:lazurite_gem', '1536x gtceu:pure_lazurite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)
    sift('lazurite_silked', '128x #c:ores/lazurite', 'minecraft:water', ['3200x gtceu:stone_dust'], ['128x gtceu:sodalite_gem', '3072x gtceu:sodalite_dust', '3072x gtceu:exquisite_lazurite_gem', '3072x gtceu:flawless_lazurite_gem', '3072x gtceu:lazurite_gem', '3072x gtceu:pure_lazurite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 12)

    // magnesite
    process('magnesite', '128x #c:raw_materials/magnesite', 'minecraft:water', ['256x gtceu:magnesite_dust', '256x gtceu:stone_dust'], ['128x gtceu:magnesium_dust', '128x gtceu:stone_dust', '256x gtceu:magnesium_dust', '256x gtceu:magnesium_dust', '256x gtceu:cobaltite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('magnesite_silked', '128x #c:ores/magnesite', 'minecraft:water', ['512x gtceu:magnesite_dust', '640x gtceu:stone_dust'], ['128x gtceu:magnesium_dust', '512x gtceu:magnesium_dust', '512x gtceu:magnesium_dust', '512x gtceu:cobaltite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // magnetite
    process('magnetite', '128x #c:raw_materials/magnetite', 'minecraft:water', ['256x gtceu:magnetite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x alltheores:gold_dust', '256x alltheores:gold_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('magnetite_silked', '128x #c:ores/magnetite', 'minecraft:water', ['512x gtceu:magnetite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x alltheores:gold_dust', '512x alltheores:gold_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('magnetite', '128x #c:raw_materials/magnetite', 'gtceu:mercury', ['256x gtceu:magnetite_dust', '256x alltheores:gold_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:gold_dust', '256x alltheores:gold_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('magnetite_silked', '128x #c:ores/magnetite', 'gtceu:mercury', ['512x gtceu:magnetite_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x gtceu:stone_dust', '512x alltheores:gold_dust', '512x alltheores:gold_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // molybdenite
    process('molybdenite', '128x #c:raw_materials/molybdenite', 'minecraft:water', ['256x gtceu:molybdenite_dust', '256x gtceu:stone_dust'], ['128x gtceu:molybdenum_dust', '128x gtceu:stone_dust', '256x gtceu:molybdenum_dust', '256x gtceu:sulfur_dust', '256x gtceu:quartzite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('molybdenite_silked', '128x #c:ores/molybdenite', 'minecraft:water', ['512x gtceu:molybdenite_dust', '640x gtceu:stone_dust'], ['128x gtceu:molybdenum_dust', '512x gtceu:molybdenum_dust', '512x gtceu:sulfur_dust', '512x gtceu:quartzite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // powellite
    process('powellite', '128x #c:raw_materials/powellite', 'minecraft:water', ['256x gtceu:powellite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x gtceu:potassium_dust', '256x gtceu:molybdenite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('powellite_silked', '128x #c:ores/powellite', 'minecraft:water', ['512x gtceu:powellite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x gtceu:potassium_dust', '512x gtceu:molybdenite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // pyrite
    process('pyrite', '128x #c:raw_materials/pyrite', 'minecraft:water', ['256x gtceu:pyrite_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:tricalcium_phosphate_dust', '256x alltheores:iron_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pyrite_silked', '128x #c:ores/pyrite', 'minecraft:water', ['512x gtceu:pyrite_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:tricalcium_phosphate_dust', '512x alltheores:iron_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // pyrolusite
    process('pyrolusite', '128x #c:raw_materials/pyrolusite', 'minecraft:water', ['256x gtceu:pyrolusite_dust', '256x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '128x gtceu:stone_dust', '256x gtceu:manganese_dust', '256x gtceu:tantalite_dust', '256x gtceu:niobium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pyrolusite_silked', '128x #c:ores/pyrolusite', 'minecraft:water', ['512x gtceu:pyrolusite_dust', '640x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '512x gtceu:manganese_dust', '512x gtceu:tantalite_dust', '512x gtceu:niobium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // pyrope
    process('pyrope', '128x #c:raw_materials/pyrope', 'minecraft:water', ['768x gtceu:pyrope_dust', '768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x gtceu:magnesium_dust', '768x gtceu:magnesium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('pyrope_silked', '128x #c:ores/pyrope', 'minecraft:water', ['1536x gtceu:pyrope_dust', '1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x gtceu:magnesium_dust', '1536x gtceu:magnesium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    sift('pyrope', '128x #c:raw_materials/pyrope', 'minecraft:water', ['768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x gtceu:exquisite_pyrope_gem', '768x gtceu:flawless_pyrope_gem', '768x gtceu:pyrope_gem', '768x gtceu:pure_pyrope_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 3)
    sift('pyrope_silked', '128x #c:ores/pyrope', 'minecraft:water', ['1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x gtceu:exquisite_pyrope_gem', '1536x gtceu:flawless_pyrope_gem', '1536x gtceu:pyrope_gem', '1536x gtceu:pure_pyrope_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)

    // rock salt
    process('rock_salt', '128x #c:raw_materials/rock_salt', 'minecraft:water', ['512x gtceu:rock_salt_dust', '512x gtceu:stone_dust'], ['128x gtceu:salt_gem', '128x gtceu:stone_dust', '512x gtceu:salt_dust', '512x gtceu:borax_dust', '512x gtceu:borax_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('rock_salt_silked', '128x #c:ores/rock_salt', 'minecraft:water', ['1024x gtceu:rock_salt_dust', '1152x gtceu:stone_dust'], ['128x gtceu:salt_gem', '1024x gtceu:salt_dust', '1024x gtceu:borax_dust', '1024x gtceu:borax_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('rock_salt', '128x #c:raw_materials/rock_salt', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:salt_gem', '128x gtceu:stone_dust', '512x gtceu:salt_dust', '512x gtceu:exquisite_rock_salt_gem', '512x gtceu:flawless_rock_salt_gem', '512x gtceu:rock_salt_gem', '512x gtceu:pure_rock_salt_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('rock_salt_silked', '128x #c:ores/rock_salt', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:salt_gem', '1024x gtceu:salt_dust', '1024x gtceu:exquisite_rock_salt_gem', '1024x gtceu:flawless_rock_salt_gem', '1024x gtceu:rock_salt_gem', '1024x gtceu:pure_rock_salt_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)

    // ruby
    process('ruby', '128x #c:raw_materials/ruby', 'minecraft:water', ['256x gtceu:ruby_dust', '256x gtceu:stone_dust'], ['128x gtceu:chromium_dust', '128x gtceu:stone_dust', '256x gtceu:chromium_dust', '256x gtceu:red_garnet_dust', '256x gtceu:chromium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('ruby_silked', '128x #c:ores/ruby', 'minecraft:water', ['512x gtceu:ruby_dust', '640x gtceu:stone_dust'], ['128x gtceu:chromium_dust', '512x gtceu:chromium_dust', '512x gtceu:red_garnet_dust', '512x gtceu:chromium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('ruby', '128x #c:raw_materials/ruby', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:chromium_dust', '128x gtceu:stone_dust', '256x gtceu:chromium_dust', '256x gtceu:exquisite_ruby_gem', '256x gtceu:flawless_ruby_gem', '256x gtceu:ruby_gem', '256x gtceu:pure_ruby_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('ruby_silked', '128x #c:ores/ruby', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:chromium_dust', '512x gtceu:chromium_dust', '512x gtceu:exquisite_ruby_gem', '512x gtceu:flawless_ruby_gem', '512x gtceu:ruby_gem', '512x gtceu:pure_ruby_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // salt
    process('salt', '128x #c:raw_materials/salt', 'minecraft:water', ['512x gtceu:salt_dust', '512x gtceu:stone_dust'], ['128x gtceu:rock_salt_gem', '128x gtceu:stone_dust', '512x gtceu:rock_salt_dust', '512x gtceu:borax_dust', '512x gtceu:borax_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('salt_silked', '128x #c:ores/salt', 'minecraft:water', ['1024x gtceu:salt_dust', '1152x gtceu:stone_dust'], ['128x gtceu:rock_salt_gem', '1024x gtceu:rock_salt_dust', '1024x gtceu:borax_dust', '1024x gtceu:borax_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('salt', '128x #c:raw_materials/salt', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:rock_salt_gem', '128x gtceu:stone_dust', '512x gtceu:rock_salt_dust', '512x gtceu:exquisite_salt_gem', '512x gtceu:flawless_salt_gem', '512x gtceu:salt_gem', '512x gtceu:pure_salt_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('salt_silked', '128x #c:ores/salt', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:rock_salt_gem', '1024x gtceu:rock_salt_dust', '1024x gtceu:exquisite_salt_gem', '1024x gtceu:flawless_salt_gem', '1024x gtceu:salt_gem', '1024x gtceu:pure_salt_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)

    // saltpeter
    process('saltpeter', '128x #c:raw_materials/saltpeter', 'minecraft:water', ['512x gtceu:saltpeter_dust', '512x gtceu:stone_dust'], ['128x gtceu:saltpeter_dust', '128x gtceu:stone_dust', '512x gtceu:saltpeter_dust', '512x gtceu:potassium_dust', '512x gtceu:salt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('saltpeter_silked', '128x #c:ores/saltpeter', 'minecraft:water', ['1024x gtceu:saltpeter_dust', '1152x gtceu:stone_dust'], ['128x gtceu:saltpeter_dust', '1024x gtceu:saltpeter_dust', '1024x gtceu:potassium_dust', '1024x gtceu:salt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    
    // sapphire
    process('sapphire', '128x #c:raw_materials/sapphire', 'minecraft:water', ['256x alltheores:sapphire_dust', '256x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '256x alltheores:aluminum_dust', '256x gtceu:green_sapphire_dust', '256x gtceu:green_sapphire_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('sapphire_silked', '128x #c:ores/sapphire', 'minecraft:water', ['512x alltheores:sapphire_dust', '640x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '512x alltheores:aluminum_dust', '512x gtceu:green_sapphire_dust', '512x gtceu:green_sapphire_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('sapphire', '128x #c:raw_materials/sapphire', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '256x alltheores:aluminum_dust', '256x gtceu:exquisite_sapphire_gem', '256x gtceu:flawless_sapphire_gem', '256x gtceu:sapphire_gem', '256x gtceu:pure_sapphire_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('sapphire_silked', '128x #c:ores/sapphire', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '512x alltheores:aluminum_dust', '512x gtceu:exquisite_sapphire_gem', '512x gtceu:flawless_sapphire_gem', '512x gtceu:sapphire_gem', '512x gtceu:pure_sapphire_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // scheelite
    process('scheelite', '128x #c:raw_materials/scheelite', 'minecraft:water', ['256x gtceu:scheelite_dust', '256x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '128x gtceu:stone_dust', '256x gtceu:manganese_dust', '256x gtceu:molybdenum_dust', '256x gtceu:calcium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('scheelite_silked', '128x #c:ores/scheelite', 'minecraft:water', ['512x gtceu:scheelite_dust', '640x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '512x gtceu:manganese_dust', '512x gtceu:molybdenum_dust', '512x gtceu:calcium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // sodalite
    process('sodalite', '128x #c:raw_materials/sodalite', 'minecraft:water', ['1536x gtceu:sodalite_dust', '1536x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '128x gtceu:stone_dust', '1536x gtceu:lazurite_dust', '6144x gtceu:lapis_dust', '1536x gtceu:lapis_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 6)
    process('sodalite_silked', '128x #c:ores/sodalite', 'minecraft:water', ['3072x gtceu:sodalite_dust', '3200x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '3072x gtceu:lazurite_dust', '12288x gtceu:lapis_dust', '3072x gtceu:lapis_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 12)
    sift('sodalite', '128x #c:raw_materials/sodalite', 'minecraft:water', ['1536x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '128x gtceu:stone_dust', '1536x gtceu:lazurite_dust', '1536x gtceu:exquisite_sodalite_gem', '1536x gtceu:flawless_sodalite_gem', '1536x gtceu:sodalite_gem', '1536x gtceu:pure_sodalite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)
    sift('sodalite_silked', '128x #c:ores/sodalite', 'minecraft:water', ['3200x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '3072x gtceu:lazurite_dust', '3072x gtceu:exquisite_sodalite_gem', '3072x gtceu:flawless_sodalite_gem', '3072x gtceu:sodalite_gem', '3072x gtceu:pure_sodalite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 12)

    // tantalite
    process('tantalite', '128x #c:raw_materials/tantalite', 'minecraft:water', ['256x gtceu:tantalite_dust', '256x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '128x gtceu:stone_dust', '256x gtceu:manganese_dust', '256x gtceu:niobium_dust', '256x gtceu:tantalum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('tantalite_silked', '128x #c:ores/tantalite', 'minecraft:water', ['512x gtceu:tantalite_dust', '640x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '512x gtceu:manganese_dust', '512x gtceu:niobium_dust', '512x gtceu:tantalum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // spessartine
    process('spessartine', '128x #c:raw_materials/spessartine', 'minecraft:water', ['768x gtceu:spessartine_dust', '768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x gtceu:manganese_dust', '768x gtceu:manganese_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('spessartine_silked', '128x #c:ores/spessartine', 'minecraft:water', ['1536x gtceu:spessartine_dust', '1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x gtceu:manganese_dust', '1536x gtceu:manganese_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    sift('spessartine', '128x #c:raw_materials/spessartine', 'minecraft:water', ['768x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '768x gtceu:red_garnet_dust', '768x gtceu:exquisite_spessartine_gem', '768x gtceu:flawless_spessartine_gem', '768x gtceu:spessartine_gem', '768x gtceu:pure_spessartine_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 3)
    sift('spessartine_silked', '128x #c:ores/spessartine', 'minecraft:water', ['1664x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '1536x gtceu:red_garnet_dust', '1536x gtceu:exquisite_spessartine_gem', '1536x gtceu:flawless_spessartine_gem', '1536x gtceu:spessartine_gem', '1536x gtceu:pure_spessartine_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)

    // sphalerite
    process('sphalerite', '128x #c:raw_materials/sphalerite', 'minecraft:water', ['256x gtceu:sphalerite_dust', '256x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '128x gtceu:stone_dust', '256x gtceu:yellow_garnet_dust', '256x gtceu:gallium_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('sphalerite_silked', '128x #c:ores/sphalerite', 'minecraft:water', ['512x gtceu:sphalerite_dust', '640x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '512x gtceu:yellow_garnet_dust', '512x gtceu:gallium_dust', '512x gtceu:cadmium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('sphalerite', '128x #c:raw_materials/sphalerite', 'gtceu:sodium_persulfate', ['256x gtceu:sphalerite_dust', '256x alltheores:zinc_dust'], ['128x gtceu:yellow_garnet_gem', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:gallium_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('sphalerite_silked', '128x #c:ores/sphalerite', 'gtceu:sodium_persulfate', ['512x gtceu:sphalerite_dust', '512x alltheores:zinc_dust', '128x gtceu:stone_dust'], ['128x gtceu:yellow_garnet_gem', '512x gtceu:stone_dust', '512x gtceu:gallium_dust', '512x gtceu:cadmium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // stibnite
    process('stibnite', '128x #c:raw_materials/stibnite', 'minecraft:water', ['256x gtceu:stibnite_dust', '256x gtceu:stone_dust'], ['128x gtceu:antimony_trioxide_dust', '128x gtceu:stone_dust', '256x gtceu:antimony_trioxide_dust', '256x gtceu:antimony_dust', '256x gtceu:cinnabar_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('stibnite_silked', '128x #c:ores/stibnite', 'minecraft:water', ['512x gtceu:stibnite_dust', '640x gtceu:stone_dust'], ['128x gtceu:antimony_trioxide_dust', '512x gtceu:antimony_trioxide_dust', '512x gtceu:antimony_dust', '512x gtceu:cinnabar_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('stibnite', '128x #c:raw_materials/stibnite', 'gtceu:sodium_persulfate', ['256x gtceu:stibnite_dust', '256x gtceu:cinnabar_dust'], ['128x gtceu:antimony_trioxide_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:antimony_dust', '256x gtceu:cinnabar_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('stibnite_silked', '128x #c:ores/stibnite', 'gtceu:sodium_persulfate', ['512x gtceu:stibnite_dust', '512x gtceu:cinnabar_dust', '128x gtceu:stone_dust'], ['128x gtceu:antimony_trioxide_dust', '512x gtceu:stone_dust', '512x gtceu:antimony_dust', '512x gtceu:cinnabar_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // tetrahedrite
    process('tetrahedrite', '128x #c:raw_materials/tetrahedrite', 'minecraft:water', ['256x gtceu:tetrahedrite_dust', '256x gtceu:stone_dust'], ['128x gtceu:antimony_dust', '128x gtceu:stone_dust', '256x gtceu:antimony_dust', '256x alltheores:zinc_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('tetrahedrite_silked', '128x #c:ores/tetrahedrite', 'minecraft:water', ['512x gtceu:tetrahedrite_dust', '640x gtceu:stone_dust'], ['128x gtceu:antimony_dust', '512x gtceu:antimony_dust', '512x alltheores:zinc_dust', '512x gtceu:cadmium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('tetrahedrite', '128x #c:raw_materials/tetrahedrite', 'gtceu:sodium_persulfate', ['256x gtceu:tetrahedrite_dust', '256x gtceu:cadmium_dust'], ['128x gtceu:antimony_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:zinc_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('tetrahedrite_silked', '128x #c:ores/tetrahedrite', 'gtceu:sodium_persulfate', ['512x gtceu:tetrahedrite_dust', '512x gtceu:cadmium_dust', '128x gtceu:stone_dust'], ['128x gtceu:antimony_dust', '512x gtceu:stone_dust', '512x alltheores:zinc_dust', '512x gtceu:cadmium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // topaz
    process('topaz', '128x #c:raw_materials/topaz', 'minecraft:water', ['256x gtceu:topaz_dust', '256x gtceu:stone_dust'], ['128x gtceu:blue_topaz_gem', '128x gtceu:stone_dust', '256x gtceu:blue_topaz_dust', '256x gtceu:blue_topaz_dust', '256x gtceu:blue_topaz_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('topaz_silked', '128x #c:ores/topaz', 'minecraft:water', ['512x gtceu:topaz_dust', '640x gtceu:stone_dust'], ['128x gtceu:blue_topaz_gem', '512x gtceu:blue_topaz_dust', '512x gtceu:blue_topaz_dust', '512x gtceu:blue_topaz_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('topaz', '128x #c:raw_materials/topaz', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:blue_topaz_gem', '128x gtceu:stone_dust', '256x gtceu:blue_topaz_dust', '256x gtceu:exquisite_topaz_gem', '256x gtceu:flawless_topaz_gem', '256x gtceu:topaz_gem', '256x gtceu:pure_topaz_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('topaz_silked', '128x #c:ores/topaz', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:blue_topaz_gem', '512x gtceu:blue_topaz_dust', '512x gtceu:exquisite_topaz_gem', '512x gtceu:flawless_topaz_gem', '512x gtceu:topaz_gem', '512x gtceu:pure_topaz_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)

    // tungstate
    process('tungstate', '128x #c:raw_materials/tungstate', 'minecraft:water', ['256x gtceu:tungstate_dust', '256x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '128x gtceu:stone_dust', '256x gtceu:manganese_dust', '256x alltheores:silver_dust', '256x gtceu:lithium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('tungstate_silked', '128x #c:ores/tungstate', 'minecraft:water', ['512x gtceu:tungstate_dust', '640x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '512x gtceu:manganese_dust', '512x alltheores:silver_dust', '512x gtceu:lithium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('tungstate', '128x #c:raw_materials/tungstate', 'gtceu:mercury', ['256x gtceu:tungstate_dust', '256x gtceu:lithium_dust'], ['128x gtceu:manganese_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x alltheores:silver_dust', '256x gtceu:lithium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('tungstate_silked', '128x #c:ores/tungstate', 'gtceu:mercury', ['512x gtceu:tungstate_dust', '512x gtceu:lithium_dust', '128x gtceu:stone_dust'], ['128x gtceu:manganese_dust', '512x gtceu:stone_dust', '512x alltheores:silver_dust', '512x gtceu:lithium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // uraninite
    process('uraninite', '128x #c:raw_materials/uraninite', 'minecraft:water', ['256x gtceu:uraninite_dust', '256x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '128x gtceu:stone_dust', '256x gtceu:uraninite_dust', '256x gtceu:thorium_dust', '256x alltheores:silver_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('uraninite_silked', '128x #c:ores/uraninite', 'minecraft:water', ['512x gtceu:uraninite_dust', '640x gtceu:stone_dust'], ['128x gtceu:uraninite_dust', '512x gtceu:uraninite_dust', '512x gtceu:thorium_dust', '512x alltheores:silver_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // wulfenite
    process('wulfenite', '128x #c:raw_materials/wulfenite', 'minecraft:water', ['256x gtceu:wulfenite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x gtceu:manganese_dust', '256x gtceu:manganese_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('wulfenite_silked', '128x #c:ores/wulfenite', 'minecraft:water', ['512x gtceu:wulfenite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x gtceu:manganese_dust', '512x gtceu:manganese_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // yellow limonite
    process('yellow_limonite', '128x #c:raw_materials/yellow_limonite', 'minecraft:water', ['256x gtceu:yellow_limonite_dust', '256x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x alltheores:nickel_dust', '256x gtceu:goethite_dust', '256x gtceu:cobalt_oxide_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('yellow_limonite_silked', '128x #c:ores/yellow_limonite', 'minecraft:water', ['512x gtceu:yellow_limonite_dust', '640x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x alltheores:nickel_dust', '512x gtceu:goethite_dust', '512x gtceu:cobalt_oxide_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('yellow_limonite', '128x #c:raw_materials/yellow_limonite', 'gtceu:sodium_persulfate', ['256x gtceu:yellow_limonite_dust', '256x gtceu:cobalt_oxide_dust'], ['128x alltheores:nickel_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:goethite_dust', '256x gtceu:cobalt_oxide_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('yellow_limonite_silked', '128x #c:ores/yellow_limonite', 'gtceu:sodium_persulfate', ['512x gtceu:yellow_limonite_dust', '512x gtceu:cobalt_oxide_dust', '128x gtceu:stone_dust'], ['128x alltheores:nickel_dust', '512x gtceu:stone_dust', '512x gtceu:goethite_dust', '512x gtceu:cobalt_oxide_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // nether quartz
    process('nether_quartz', '128x #c:raw_materials/nether_quartz', 'minecraft:water', ['512x gtceu:nether_quartz_dust', '512x gtceu:stone_dust'], ['128x gtceu:quartzite_gem', '128x gtceu:stone_dust', '512x gtceu:quartzite_dust', '512x gtceu:quartzite_dust', '512x gtceu:quartzite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('nether_quartz_silked', '128x #c:ores/nether_quartz', 'minecraft:water', ['1024x gtceu:nether_quartz_dust', '1152x gtceu:stone_dust'], ['128x gtceu:quartzite_gem', '1024x gtceu:quartzite_dust', '1024x gtceu:quartzite_dust', '1024x gtceu:quartzite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('nether_quartz', '128x #c:raw_materials/nether_quartz', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:quartzite_gem', '128x gtceu:stone_dust', '512x gtceu:quartzite_dust', '512x gtceu:exquisite_nether_quartz_gem', '512x gtceu:flawless_nether_quartz_gem', '512x minecraft:quartz', '512x gtceu:pure_nether_quartz_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('nether_quartz_silked', '128x #c:ores/nether_quartz', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:quartzite_gem', '1024x gtceu:quartzite_dust', '1024x gtceu:exquisite_nether_quartz_gem', '1024x gtceu:flawless_nether_quartz_gem', '1024x minecraft:quartz', '1024x gtceu:pure_nether_quartz_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    
    // certus quartz
    process('certus_quartz', '128x #c:raw_materials/certus_quartz', 'minecraft:water', ['512x ae2:certus_quartz_dust', '512x gtceu:stone_dust'], ['128x minecraft:quartz', '128x gtceu:stone_dust', '512x gtceu:nether_quartz_dust', '512x gtceu:barite_dust', '512x gtceu:barite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('certus_quartz_silked', '128x #c:ores/certus_quartz', 'minecraft:water', ['1024x ae2:certus_quartz_dust', '1152x gtceu:stone_dust'], ['128x minecraft:quartz', '1024x gtceu:nether_quartz_dust', '1024x gtceu:barite_dust', '1024x gtceu:barite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('certus_quartz', '128x #c:raw_materials/certus_quartz', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x minecraft:quartz', '128x gtceu:stone_dust', '512x gtceu:nether_quartz_dust', '512x gtceu:exquisite_certus_quartz_gem', '512x gtceu:flawless_certus_quartz_gem', '512x gtceu:certus_quartz_gem', '512x gtceu:pure_certus_quartz_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('certus_quartz_silked', '128x #c:ores/certus_quartz', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x minecraft:quartz', '1024x gtceu:nether_quartz_dust', '1024x gtceu:exquisite_certus_quartz_gem', '1024x gtceu:flawless_certus_quartz_gem', '1024x gtceu:certus_quartz_gem', '1024x gtceu:pure_certus_quartz_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    
    // quartzite
    process('quartzite', '128x #c:raw_materials/quartzite', 'minecraft:water', ['512x gtceu:quartzite_dust', '512x gtceu:stone_dust'], ['128x ae2:certus_quartz_crystal', '128x gtceu:stone_dust', '512x ae2:certus_quartz_dust', '512x gtceu:barite_dust', '512x gtceu:barite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('quartzite_silked', '128x #c:ores/quartzite', 'minecraft:water', ['1024x gtceu:quartzite_dust', '1152x gtceu:stone_dust'], ['128x ae2:certus_quartz_crystal', '1024x ae2:certus_quartz_dust', '1024x gtceu:barite_dust', '1024x gtceu:barite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('quartzite', '128x #c:raw_materials/quartzite', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x ae2:certus_quartz_crystal', '128x gtceu:stone_dust', '512x ae2:certus_quartz_dust', '512x gtceu:exquisite_quartzite_gem', '512x gtceu:flawless_quartzite_gem', '512x gtceu:quartzite_gem', '512x gtceu:pure_quartzite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('quartzite_silked', '128x #c:ores/quartzite', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x ae2:certus_quartz_crystal', '1024x ae2:certus_quartz_dust', '1024x gtceu:exquisite_quartzite_gem', '1024x gtceu:flawless_quartzite_gem', '1024x gtceu:quartzite_gem', '1024x gtceu:pure_quartzite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    
    // graphite
    process('graphite', '128x #c:raw_materials/graphite', 'minecraft:water', ['256x gtceu:graphite_dust', '256x gtceu:stone_dust'], ['128x gtceu:carbon_dust', '128x gtceu:stone_dust', '256x gtceu:carbon_dust', '256x gtceu:carbon_dust', '256x gtceu:carbon_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('graphite_silked', '128x #c:ores/graphite', 'minecraft:water', ['512x gtceu:graphite_dust', '640x gtceu:stone_dust'], ['128x gtceu:carbon_dust', '512x gtceu:carbon_dust', '512x gtceu:carbon_dust', '512x gtceu:carbon_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // bornite
    process('bornite', '128x #c:raw_materials/bornite', 'minecraft:water', ['256x gtceu:bornite_dust', '256x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '128x gtceu:stone_dust', '256x gtceu:pyrite_dust', '256x gtceu:cobalt_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('bornite_silked', '128x #c:ores/bornite', 'minecraft:water', ['512x gtceu:bornite_dust', '640x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '512x gtceu:pyrite_dust', '512x gtceu:cobalt_dust', '512x gtceu:cadmium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('bornite', '128x #c:raw_materials/bornite', 'gtceu:mercury', ['256x gtceu:bornite_dust', '256x alltheores:gold_dust'], ['128x gtceu:pyrite_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:cobalt_dust', '256x gtceu:cadmium_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('bornite_silked', '128x #c:ores/bornite', 'gtceu:mercury', ['512x gtceu:bornite_dust', '512x alltheores:gold_dust', '128x gtceu:stone_dust'], ['128x gtceu:pyrite_dust', '512x gtceu:stone_dust', '512x gtceu:cobalt_dust', '512x gtceu:cadmium_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // chalcocite
    process('chalcocite', '128x #c:raw_materials/chalcocite', 'minecraft:water', ['256x gtceu:chalcocite_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:massicot_dust', '256x alltheores:silver_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('chalcocite_silked', '128x #c:ores/chalcocite', 'minecraft:water', ['512x gtceu:chalcocite_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:massicot_dust', '512x alltheores:silver_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // realgar
    process('realgar', '128x #c:raw_materials/realgar', 'minecraft:water', ['256x gtceu:realgar_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:antimony_dust', '256x gtceu:barite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('realgar_silked', '128x #c:ores/realgar', 'minecraft:water', ['512x gtceu:realgar_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:antimony_dust', '512x gtceu:barite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('realgar', '128x #c:raw_materials/realgar', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:exquisite_realgar_gem', '256x gtceu:flawless_realgar_gem', '256x gtceu:realgar_gem', '256x gtceu:pure_realgar_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('realgar_silked', '128x #c:ores/realgar', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:exquisite_realgar_gem', '512x gtceu:flawless_realgar_gem', '512x gtceu:realgar_gem', '512x gtceu:pure_realgar_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    
    // bastnasite
    process('bastnasite', '128x #c:raw_materials/bastnasite', 'minecraft:water', ['512x gtceu:bastnasite_dust', '512x gtceu:stone_dust'], ['128x gtceu:neodymium_dust', '128x gtceu:stone_dust', '512x gtceu:neodymium_dust', '512x gtceu:rare_earth_dust', '512x gtceu:rare_earth_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('bastnasite_silked', '128x #c:ores/bastnasite', 'minecraft:water', ['1024x gtceu:bastnasite_dust', '1152x gtceu:stone_dust'], ['128x gtceu:neodymium_dust', '1024x gtceu:neodymium_dust', '1024x gtceu:rare_earth_dust', '1024x gtceu:rare_earth_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    
    // pentlandite
    process('pentlandite', '128x #c:raw_materials/pentlandite', 'minecraft:water', ['256x gtceu:pentlandite_dust', '256x gtceu:stone_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x alltheores:iron_dust', '256x gtceu:sulfur_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pentlandite_silked', '128x #c:ores/pentlandite', 'minecraft:water', ['512x gtceu:pentlandite_dust', '640x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x alltheores:iron_dust', '512x gtceu:sulfur_dust', '512x gtceu:cobalt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('pentlandite', '128x #c:raw_materials/pentlandite', 'gtceu:sodium_persulfate', ['256x gtceu:pentlandite_dust', '256x gtceu:cobalt_dust'], ['128x alltheores:iron_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:cobalt_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('pentlandite_silked', '128x #c:ores/pentlandite', 'gtceu:sodium_persulfate', ['512x gtceu:pentlandite_dust', '512x gtceu:cobalt_dust', '128x gtceu:stone_dust'], ['128x alltheores:iron_dust', '512x gtceu:stone_dust', '512x gtceu:sulfur_dust', '512x gtceu:cobalt_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)

    // spodumene
    process('spodumene', '128x #c:raw_materials/spodumene', 'minecraft:water', ['256x gtceu:spodumene_dust', '256x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '256x alltheores:aluminum_dust', '256x gtceu:lithium_dust', '256x gtceu:lithium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('spodumene_silked', '128x #c:ores/spodumene', 'minecraft:water', ['512x gtceu:spodumene_dust', '640x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '512x alltheores:aluminum_dust', '512x gtceu:lithium_dust', '512x gtceu:lithium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // lepidolite
    process('lepidolite', '128x #c:raw_materials/lepidolite', 'minecraft:water', ['512x gtceu:lepidolite_dust', '512x gtceu:stone_dust'], ['128x gtceu:lithium_dust', '128x gtceu:stone_dust', '512x gtceu:lithium_dust', '512x gtceu:caesium_dust', '512x gtceu:boron_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('lepidolite_silked', '128x #c:ores/lepidolite', 'minecraft:water', ['1024x gtceu:lepidolite_dust', '1152x gtceu:stone_dust'], ['128x gtceu:lithium_dust', '1024x gtceu:lithium_dust', '1024x gtceu:caesium_dust', '1024x gtceu:boron_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    
    // glauconite sand
    process('glauconite_sand', '128x #c:raw_materials/glauconite_sand', 'minecraft:water', ['768x gtceu:glauconite_sand_dust', '768x gtceu:stone_dust'], ['128x gtceu:sodium_dust', '128x gtceu:stone_dust', '768x gtceu:sodium_dust', '768x alltheores:aluminum_dust', '768x alltheores:iron_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('sglauconite_sand_silked', '128x #c:ores/glauconite_sand', 'minecraft:water', ['1536x gtceu:glauconite_sand_dust', '1664x gtceu:stone_dust'], ['128x gtceu:sodium_dust', '1536x gtceu:sodium_dust', '1536x alltheores:aluminum_dust', '1536x alltheores:iron_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    
    // malachite
    process('malachite', '128x #c:raw_materials/malachite', 'minecraft:water', ['256x gtceu:malachite_dust', '256x gtceu:stone_dust'], ['128x gtceu:goethite_dust', '128x gtceu:stone_dust', '256x gtceu:goethite_dust', '256x gtceu:calcite_dust', '256x gtceu:zincite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('malachite_silked', '128x #c:ores/malachite', 'minecraft:water', ['512x gtceu:malachite_dust', '640x gtceu:stone_dust'], ['128x gtceu:goethite_dust', '512x gtceu:goethite_dust', '512x gtceu:calcite_dust', '512x gtceu:zincite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    bathe('malachite', '128x #c:raw_materials/malachite', 'gtceu:sodium_persulfate', ['256x gtceu:malachite_dust', '256x gtceu:zincite_dust'], ['128x gtceu:goethite_dust', '128x gtceu:stone_dust', '256x gtceu:stone_dust', '256x gtceu:calcite_dust', '256x gtceu:zincite_dust'], [2800, 1100, 7900, 3300, 6500], [300, 100, 650, 0, 850], 1)
    bathe('malachite_silked', '128x #c:ores/malachite', 'gtceu:sodium_persulfate', ['512x gtceu:malachite_dust', '512x gtceu:zincite_dust', '128x gtceu:stone_dust'], ['128x gtceu:goethite_dust', '512x gtceu:stone_dust', '512x gtceu:calcite_dust', '512x gtceu:zincite_dust'], [6500, 7900, 3300, 6500], [850, 650, 0, 850], 2)
    sift('malachite', '128x #c:raw_materials/malachite', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:goethite_dust', '128x gtceu:stone_dust', '256x gtceu:goethite_dust', '256x gtceu:exquisite_malachite_gem', '256x gtceu:flawless_malachite_gem', '256x gtceu:malachite_gem', '256x gtceu:pure_malachite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('malachite_silked', '128x #c:ores/malachite', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:goethite_dust', '512x gtceu:goethite_dust', '512x gtceu:exquisite_malachite_gem', '512x gtceu:flawless_malachite_gem', '512x gtceu:malachite_gem', '512x gtceu:pure_malachite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    
    // mica
    process('mica', '128x #c:raw_materials/mica', 'minecraft:water', ['512x gtceu:mica_dust', '512x gtceu:stone_dust'], ['128x gtceu:potassium_dust', '128x gtceu:stone_dust', '512x gtceu:potassium_dust', '512x alltheores:aluminum_dust', '512x alltheores:aluminum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('mica_silked', '128x #c:ores/mica', 'minecraft:water', ['1024x gtceu:mica_dust', '1152x gtceu:stone_dust'], ['128x gtceu:potassium_dust', '1024x gtceu:potassium_dust', '1024x alltheores:aluminum_dust', '1024x alltheores:aluminum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    
    // barite
    process('barite', '128x #c:raw_materials/barite', 'minecraft:water', ['256x gtceu:barite_dust', '256x gtceu:stone_dust'], ['128x gtceu:barite_dust', '128x gtceu:stone_dust', '256x gtceu:barite_dust', '256x gtceu:barite_dust', '256x gtceu:barite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('barite_silked', '128x #c:ores/barite', 'minecraft:water', ['512x gtceu:barite_dust', '640x gtceu:stone_dust'], ['128x gtceu:barite_dust', '512x gtceu:barite_dust', '512x gtceu:barite_dust', '512x gtceu:barite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // alunite
    process('alunite', '128x #c:raw_materials/alunite', 'minecraft:water', ['768x gtceu:alunite_dust', '768x gtceu:stone_dust'], ['128x gtceu:alunite_dust', '128x gtceu:stone_dust', '768x gtceu:alunite_dust', '768x gtceu:alunite_dust', '768x gtceu:alunite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('alunite_silked', '128x #c:ores/alunite', 'minecraft:water', ['1536x gtceu:alunite_dust', '1664x gtceu:stone_dust'], ['128x gtceu:alunite_dust', '1536x gtceu:alunite_dust', '1536x gtceu:alunite_dust', '1536x gtceu:alunite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
    
    // talc
    process('talc', '128x #c:raw_materials/talc', 'minecraft:water', ['512x gtceu:talc_dust', '512x gtceu:stone_dust'], ['128x gtceu:clay_dust', '128x gtceu:stone_dust', '512x gtceu:clay_dust', '512x gtceu:carbon_dust', '512x gtceu:clay_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('talc_silked', '128x #c:ores/talc', 'minecraft:water', ['1024x gtceu:talc_dust', '1152x gtceu:stone_dust'], ['128x gtceu:clay_dust', '1024x gtceu:clay_dust', '1024x gtceu:carbon_dust', '1024x gtceu:clay_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    
    // soapstone
    process('soapstone', '128x #c:raw_materials/soapstone', 'minecraft:water', ['768x gtceu:soapstone_dust', '768x gtceu:stone_dust'], ['128x gtceu:silicon_dioxide_dust', '128x gtceu:stone_dust', '768x gtceu:silicon_dioxide_dust', '768x gtceu:magnesium_dust', '768x gtceu:calcite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('soapstone_silked', '128x #c:ores/soapstone', 'minecraft:water', ['1536x gtceu:soapstone_dust', '1664x gtceu:stone_dust'], ['128x gtceu:silicon_dioxide_dust', '1536x gtceu:silicon_dioxide_dust', '1536x gtceu:magnesium_dust', '1536x gtceu:calcite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)

    // kyanite
    process('kyanite', '128x #c:raw_materials/kyanite', 'minecraft:water', ['256x gtceu:kyanite_dust', '256x gtceu:stone_dust'], ['128x gtceu:talc_dust', '128x gtceu:stone_dust', '256x gtceu:talc_dust', '256x alltheores:aluminum_dust', '256x gtceu:silicon_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('kyanite_silked', '128x #c:ores/kyanite', 'minecraft:water', ['512x gtceu:kyanite_dust', '640x gtceu:stone_dust'], ['128x gtceu:talc_dust', '512x gtceu:talc_dust', '512x alltheores:aluminum_dust', '512x gtceu:silicon_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // pyrochlore
    process('pyrochlore', '128x #c:raw_materials/pyrochlore', 'minecraft:water', ['256x gtceu:pyrochlore_dust', '256x gtceu:stone_dust'], ['128x gtceu:apatite_gem', '128x gtceu:stone_dust', '256x gtceu:apatite_dust', '256x gtceu:calcium_dust', '256x gtceu:niobium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pyrochlore_silked', '128x #c:ores/pyrochlore', 'minecraft:water', ['512x gtceu:pyrochlore_dust', '640x gtceu:stone_dust'], ['128x gtceu:apatite_gem', '512x gtceu:apatite_dust', '512x gtceu:calcium_dust', '512x gtceu:niobium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // oilsands
    process('oilsands', '128x #c:raw_materials/oilsands', 'minecraft:water', ['256x gtceu:oilsands_dust', '256x gtceu:stone_dust'], ['128x gtceu:oilsands_dust', '128x gtceu:stone_dust', '256x gtceu:oilsands_dust', '256x gtceu:oilsands_dust', '256x gtceu:oilsands_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('oilsands_silked', '128x #c:ores/oilsands', 'minecraft:water', ['512x gtceu:oilsands_dust', '640x gtceu:stone_dust'], ['128x gtceu:oilsands_dust', '512x gtceu:oilsands_dust', '512x gtceu:oilsands_dust', '512x gtceu:oilsands_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // olivine
    process('olivine', '128x #c:raw_materials/olivine', 'minecraft:water', ['512x gtceu:olivine_dust', '512x gtceu:stone_dust'], ['128x gtceu:pyrope_gem', '128x gtceu:stone_dust', '512x gtceu:pyrope_dust', '512x gtceu:magnesium_dust', '512x gtceu:manganese_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('olivine_silked', '128x #c:ores/olivine', 'minecraft:water', ['1024x gtceu:olivine_dust', '1152x gtceu:stone_dust'], ['128x gtceu:pyrope_gem', '1024x gtceu:pyrope_dust', '1024x gtceu:magnesium_dust', '1024x gtceu:manganese_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)
    sift('olivine', '128x #c:raw_materials/olivine', 'minecraft:water', ['512x gtceu:stone_dust'], ['128x gtceu:pyrope_gem', '128x gtceu:stone_dust', '512x gtceu:pyrope_dust', '512x gtceu:exquisite_olivine_gem', '512x gtceu:flawless_olivine_gem', '512x gtceu:olivine_gem', '512x gtceu:pure_olivine_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    sift('olivine_silked', '128x #c:ores/olivine', 'minecraft:water', ['1152x gtceu:stone_dust'], ['128x gtceu:pyrope_gem', '1024x gtceu:pyrope_dust', '1024x gtceu:exquisite_olivine_gem', '1024x gtceu:flawless_olivine_gem', '1024x gtceu:olivine_gem', '1024x gtceu:pure_olivine_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    
    // opal
    process('opal', '128x #c:raw_materials/opal', 'minecraft:water', ['256x gtceu:opal_dust', '256x gtceu:stone_dust'], ['128x gtceu:opal_gem', '128x gtceu:stone_dust', '256x gtceu:opal_dust', '256x gtceu:opal_dust', '256x gtceu:opal_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('opal_silked', '128x #c:ores/opal', 'minecraft:water', ['512x gtceu:opal_dust', '640x gtceu:stone_dust'], ['128x gtceu:opal_gem', '512x gtceu:opal_dust', '512x gtceu:opal_dust', '512x gtceu:opal_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('opal', '128x #c:raw_materials/opal', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:opal_gem', '128x gtceu:stone_dust', '256x gtceu:opal_dust', '256x gtceu:exquisite_opal_gem', '256x gtceu:flawless_opal_gem', '256x gtceu:opal_gem', '256x gtceu:pure_opal_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('opal_silked', '128x #c:ores/opal', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:opal_gem', '512x gtceu:opal_dust', '512x gtceu:exquisite_opal_gem', '512x gtceu:flawless_opal_gem', '512x gtceu:opal_gem', '512x gtceu:pure_opal_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    
    // amethyst
    process('amethyst', '128x #c:raw_materials/amethyst', 'minecraft:water', ['256x gtceu:amethyst_dust', '256x gtceu:stone_dust'], ['128x minecraft:amethyst_shard', '128x gtceu:stone_dust', '256x gtceu:amethyst_dust', '256x gtceu:amethyst_dust', '256x gtceu:amethyst_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('amethyst_silked', '128x #c:ores/amethyst', 'minecraft:water', ['512x gtceu:amethyst_dust', '640x gtceu:stone_dust'], ['128x minecraft:amethyst_shard', '512x gtceu:amethyst_dust', '512x gtceu:amethyst_dust', '512x gtceu:amethyst_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('amethyst', '128x #c:raw_materials/amethyst', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x minecraft:amethyst_shard', '128x gtceu:stone_dust', '256x gtceu:amethyst_dust', '256x gtceu:exquisite_amethyst_gem', '256x gtceu:flawless_amethyst_gem', '256x minecraft:amethyst_shard', '256x gtceu:pure_amethyst_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('amethyst_silked', '128x #c:ores/amethyst', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x minecraft:amethyst_shard', '512x gtceu:amethyst_dust', '512x gtceu:exquisite_amethyst_gem', '512x gtceu:flawless_amethyst_gem', '512x minecraft:amethyst_shard', '512x gtceu:pure_amethyst_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    
    // lapis
    process('lapis', '128x #c:raw_materials/lapis', 'minecraft:water', ['1536x gtceu:lapis_dust', '1536x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '128x gtceu:stone_dust', '1536x gtceu:lazurite_dust', '6144x gtceu:sodalite_dust', '1536x gtceu:pyrite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 6)
    process('lapis_silked', '128x #c:ores/lapis', 'minecraft:water', ['3072x gtceu:lapis_dust', '3200x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '3072x gtceu:lazurite_dust', '12288x gtceu:sodalite_dust', '3072x gtceu:pyrite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 12)
    sift('lapis', '128x #c:raw_materials/lapis', 'minecraft:water', ['1536x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '128x gtceu:stone_dust', '1536x gtceu:lazurite_dust', '1536x gtceu:exquisite_lapis_gem', '1536x gtceu:flawless_lapis_gem', '1536x minecraft:lapis_lazuli', '1536x gtceu:pure_lapis_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 6)
    sift('lapis_silked', '128x #c:ores/lapis', 'minecraft:water', ['3200x gtceu:stone_dust'], ['128x gtceu:lazurite_gem', '3072x gtceu:lazurite_dust', '3072x gtceu:exquisite_lapis_gem', '3072x gtceu:flawless_lapis_gem', '3072x minecraft:lapis_lazuli', '3072x gtceu:pure_lapis_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 12)
    
    // apatite
    process('apatite', '128x #c:raw_materials/apatite', 'minecraft:water', ['1024x gtceu:apatite_dust', '1024x gtceu:stone_dust'], ['128x gtceu:tricalcium_phosphate_dust', '128x gtceu:stone_dust', '1024x gtceu:tricalcium_phosphate_dust', '2048x gtceu:phosphate_dust', '1024x gtceu:pyrochlore_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 4)
    process('apatite_silked', '128x #c:ores/apatite', 'minecraft:water', ['2048x gtceu:apatite_dust', '2176x gtceu:stone_dust'], ['128x gtceu:tricalcium_phosphate_dust', '2048x gtceu:tricalcium_phosphate_dust', '4096x gtceu:phosphate_dust', '2048x gtceu:pyrochlore_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 8)
    sift('apatite', '128x #c:raw_materials/apatite', 'minecraft:water', ['1024x gtceu:stone_dust'], ['128x gtceu:tricalcium_phosphate_dust', '128x gtceu:stone_dust', '1024x gtceu:tricalcium_phosphate_dust', '1024x gtceu:exquisite_apatite_gem', '1024x gtceu:flawless_apatite_gem', '1024x gtceu:apatite_gem', '1024x gtceu:pure_apatite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    sift('apatite_silked', '128x #c:ores/apatite', 'minecraft:water', ['2176x gtceu:stone_dust'], ['128x gtceu:tricalcium_phosphate_dust', '2048x gtceu:tricalcium_phosphate_dust', '2048x gtceu:exquisite_apatite_gem', '2048x gtceu:flawless_apatite_gem', '2048x gtceu:apatite_gem', '2048x gtceu:pure_apatite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 8)
    
    // tricalcium phosphate
    process('tricalcium_phosphate', '128x #c:raw_materials/tricalcium_phosphate', 'minecraft:water', ['768x gtceu:tricalcium_phosphate_dust', '768x gtceu:stone_dust'], ['128x gtceu:apatite_gem', '128x gtceu:stone_dust', '768x gtceu:apatite_dust', '768x gtceu:phosphate_dust', '768x gtceu:pyrochlore_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('tricalcium_phosphate_silked', '128x #c:ores/tricalcium_phosphate', 'minecraft:water', ['1536x gtceu:tricalcium_phosphate_dust', '1664x gtceu:stone_dust'], ['128x gtceu:apatite_gem', '1536x gtceu:apatite_dust', '1536x gtceu:phosphate_dust', '1536x gtceu:pyrochlore_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)
   
    // red garnet
    process('red_garnet', '128x #c:raw_materials/red_garnet', 'minecraft:water', ['1024x gtceu:red_garnet_dust', '1024x gtceu:stone_dust'], ['128x gtceu:spessartine_gem', '128x gtceu:stone_dust', '1024x gtceu:spessartine_dust', '1024x gtceu:pyrope_dust', '1024x gtceu:almandine_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 4)
    process('red_garnet_silked', '128x #c:ores/red_garnet', 'minecraft:water', ['2048x gtceu:red_garnet_dust', '2176x gtceu:stone_dust'], ['128x gtceu:spessartine_gem', '2048x gtceu:spessartine_dust', '2048x gtceu:pyrope_dust', '2048x gtceu:almandine_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 8)
    sift('red_garnet', '128x #c:raw_materials/red_garnet', 'minecraft:water', ['1024x gtceu:stone_dust'], ['128x gtceu:spessartine_gem', '128x gtceu:stone_dust', '1024x gtceu:spessartine_dust', '1024x gtceu:exquisite_red_garnet_gem', '1024x gtceu:flawless_red_garnet_gem', '1024x gtceu:red_garnet_gem', '1024x gtceu:pure_red_garnet_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    sift('red_garnet_silked', '128x #c:ores/red_garnet', 'minecraft:water', ['2176x gtceu:stone_dust'], ['128x gtceu:spessartine_gem', '2048x gtceu:spessartine_dust', '2048x gtceu:exquisite_red_garnet_gem', '2048x gtceu:flawless_red_garnet_gem', '2048x gtceu:red_garnet_gem', '2048x gtceu:pure_red_garnet_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 8)
    
    // yellow garnet
    process('yellow_garnet', '128x #c:raw_materials/yellow_garnet', 'minecraft:water', ['1024x gtceu:yellow_garnet_dust', '1024x gtceu:stone_dust'], ['128x gtceu:andradite_gem', '128x gtceu:stone_dust', '1024x gtceu:andradite_dust', '1024x gtceu:grossular_dust', '1024x gtceu:uvarovite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 4)
    process('yellow_garnet_silked', '128x #c:ores/yellow_garnet', 'minecraft:water', ['2048x gtceu:yellow_garnet_dust', '2176x gtceu:stone_dust'], ['128x gtceu:andradite_gem', '2048x gtceu:andradite_dust', '2048x gtceu:grossular_dust', '2048x gtceu:uvarovite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 8)
    sift('yellow_garnet', '128x #c:raw_materials/yellow_garnet', 'minecraft:water', ['1024x gtceu:stone_dust'], ['128x gtceu:andradite_gem', '128x gtceu:stone_dust', '1024x gtceu:andradite_dust', '1024x gtceu:exquisite_yellow_garnet_gem', '1024x gtceu:flawless_yellow_garnet_gem', '1024x gtceu:yellow_garnet_gem', '1024x gtceu:pure_yellow_garnet_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    sift('yellow_garnet_silked', '128x #c:ores/yellow_garnet', 'minecraft:water', ['2176x gtceu:stone_dust'], ['128x gtceu:andradite_gem', '2048x gtceu:andradite_dust', '2048x gtceu:exquisite_yellow_garnet_gem', '2048x gtceu:flawless_yellow_garnet_gem', '2048x gtceu:yellow_garnet_gem', '2048x gtceu:pure_yellow_garnet_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 8)
    
    // vanadium magnetite
    process('vanadium_magnetite', '128x #c:raw_materials/vanadium_magnetite', 'minecraft:water', ['256x gtceu:vanadium_magnetite_dust', '256x gtceu:stone_dust'], ['128x gtceu:magnetite_dust', '128x gtceu:stone_dust', '256x gtceu:magnetite_dust', '256x gtceu:magnetite_dust', '256x gtceu:vanadium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('vanadium_magnetite_silked', '128x #c:ores/vanadium_magnetite', 'minecraft:water', ['512x gtceu:vanadium_magnetite_dust', '640x gtceu:stone_dust'], ['128x gtceu:magnetite_dust', '512x gtceu:magnetite_dust', '512x gtceu:magnetite_dust', '512x gtceu:vanadium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // pollucite
    process('pollucite', '128x #c:raw_materials/pollucite', 'minecraft:water', ['256x gtceu:pollucite_dust', '256x gtceu:stone_dust'], ['128x gtceu:caesium_dust', '128x gtceu:stone_dust', '256x gtceu:caesium_dust', '256x alltheores:aluminum_dust', '256x gtceu:potassium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pollucite_silked', '128x #c:ores/pollucite', 'minecraft:water', ['512x gtceu:pollucite_dust', '640x gtceu:stone_dust'], ['128x gtceu:caesium_dust', '512x gtceu:caesium_dust', '512x alltheores:aluminum_dust', '512x gtceu:potassium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // bentonite
    process('bentonite', '128x #c:raw_materials/bentonite', 'minecraft:water', ['768x gtceu:bentonite_dust', '768x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '768x alltheores:aluminum_dust', '768x gtceu:calcium_dust', '768x gtceu:magnesium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('bentonite_silked', '128x #c:ores/bentonite', 'minecraft:water', ['1536x gtceu:bentonite_dust', '1664x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '1536x alltheores:aluminum_dust', '1536x gtceu:calcium_dust', '1536x gtceu:magnesium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)

    // fuller's earth
    process('fullers_earth', '128x #c:raw_materials/fullers_earth', 'minecraft:water', ['512x gtceu:fullers_earth_dust', '512x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '128x gtceu:stone_dust', '512x alltheores:aluminum_dust', '512x gtceu:silicon_dust', '512x gtceu:magnesium_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('fullers_earth_silked', '128x #c:ores/fullers_earth', 'minecraft:water', ['1024x gtceu:fullers_earth_dust', '1152x gtceu:stone_dust'], ['128x alltheores:aluminum_dust', '1024x alltheores:aluminum_dust', '1024x gtceu:silicon_dust', '1024x gtceu:magnesium_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)

    // pitchblende
    process('pitchblende', '128x #c:raw_materials/pitchblende', 'minecraft:water', ['256x gtceu:pitchblende_dust', '256x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '128x gtceu:stone_dust', '256x gtceu:thorium_dust', '256x gtceu:uraninite_dust', '256x alltheores:lead_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('pitchblende_silked', '128x #c:ores/pitchblende', 'minecraft:water', ['512x gtceu:pitchblende_dust', '640x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '512x gtceu:thorium_dust', '512x gtceu:uraninite_dust', '512x alltheores:lead_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)

    // monazite
    process('monazite', '128x #c:raw_materials/monazite', 'minecraft:water', ['1024x gtceu:monazite_dust', '1024x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '128x gtceu:stone_dust', '1024x gtceu:thorium_dust', '2048x gtceu:neodymium_dust', '1024x gtceu:rare_earth_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 4)
    process('monazite_silked', '128x #c:ores/monazite', 'minecraft:water', ['2048x gtceu:monazite_dust', '2176x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '2048x gtceu:thorium_dust', '4096x gtceu:neodymium_dust', '2048x gtceu:rare_earth_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 8)
    sift('monazite', '128x #c:raw_materials/monazite', 'minecraft:water', ['1024x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '128x gtceu:stone_dust', '1024x gtceu:thorium_dust', '1024x gtceu:exquisite_monazite_gem', '1024x gtceu:flawless_monazite_gem', '1024x gtceu:monazite_gem', '1024x gtceu:pure_monazite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 4)
    sift('monazite_silked', '128x #c:ores/monazite', 'minecraft:water', ['2176x gtceu:stone_dust'], ['128x gtceu:thorium_dust', '2048x gtceu:thorium_dust', '2048x gtceu:exquisite_monazite_gem', '2048x gtceu:flawless_monazite_gem', '2048x gtceu:monazite_gem', '2048x gtceu:pure_monazite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 8)

    // trona
    process('trona', '128x #c:raw_materials/trona', 'minecraft:water', ['512x gtceu:trona_dust', '512x gtceu:stone_dust'], ['128x gtceu:sodium_dust', '128x gtceu:stone_dust', '512x gtceu:sodium_dust', '512x gtceu:soda_ash_dust', '512x gtceu:soda_ash_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 2)
    process('trona_silked', '128x #c:ores/trona', 'minecraft:water', ['1024x gtceu:trona_dust', '1152x gtceu:stone_dust'], ['128x gtceu:sodium_dust', '1024x gtceu:sodium_dust', '1024x gtceu:soda_ash_dust', '1024x gtceu:soda_ash_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 4)

    // gypsum
    process('gypsum', '128x #c:raw_materials/gypsum', 'minecraft:water', ['256x gtceu:gypsum_dust', '256x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '128x gtceu:stone_dust', '256x gtceu:sulfur_dust', '256x gtceu:calcium_dust', '256x gtceu:salt_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('gypsum_silked', '128x #c:ores/gypsum', 'minecraft:water', ['512x gtceu:gypsum_dust', '640x gtceu:stone_dust'], ['128x gtceu:sulfur_dust', '512x gtceu:sulfur_dust', '512x gtceu:calcium_dust', '512x gtceu:salt_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // zeolite
    process('zeolite', '128x #c:raw_materials/zeolite', 'minecraft:water', ['768x gtceu:zeolite_dust', '768x gtceu:stone_dust'], ['128x gtceu:calcium_dust', '128x gtceu:stone_dust', '768x gtceu:calcium_dust', '768x gtceu:silicon_dust', '768x alltheores:aluminum_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 3)
    process('zeolite_silked', '128x #c:ores/zeolite', 'minecraft:water', ['1536x gtceu:zeolite_dust', '1664x gtceu:stone_dust'], ['128x gtceu:calcium_dust', '1536x gtceu:calcium_dust', '1536x gtceu:silicon_dust', '1536x alltheores:aluminum_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 6)

    // redstone
    process('redstone', '128x #c:raw_materials/redstone', 'minecraft:water', ['1280x minecraft:redstone', '1280x gtceu:stone_dust'], ['128x gtceu:cinnabar_gem', '128x gtceu:stone_dust', '1280x gtceu:cinnabar_dust', '1280x gtceu:rare_earth_dust', '1280x minecraft:glowstone_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 5)
    process('redstone_silked', '128x #c:ores/redstone', 'minecraft:water', ['2560x minecraft:redstone', '2688x gtceu:stone_dust'], ['128x gtceu:cinnabar_gem', '2560x gtceu:cinnabar_dust', '2560x gtceu:rare_earth_dust', '2560x minecraft:glowstone_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 10)
    
    // electrotine
    process('electrotine', '128x #c:raw_materials/electrotine', 'minecraft:water', ['1280x gtceu:electrotine_dust', '1280x gtceu:stone_dust'], ['128x appflux:redstone_crystal', '128x gtceu:stone_dust', '1280x minecraft:redstone', '1280x alltheores:electrum_dust', '1280x alltheores:diamond_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 5)
    process('electrotine_silked', '128x #c:ores/electrotine', 'minecraft:water', ['2560x gtceu:electrotine_dust', '2688x gtceu:stone_dust'], ['128x appflux:redstone_crystal', '2560x minecraft:redstone', '2560x alltheores:electrum_dust', '2560x alltheores:diamond_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 10)
    
    // diatomite
    process('diatomite', '128x #c:raw_materials/diatomite', 'minecraft:water', ['256x gtceu:diatomite_dust', '256x gtceu:stone_dust'], ['128x gtceu:hematite_dust', '128x gtceu:stone_dust', '256x gtceu:hematite_dust', '256x alltheores:sapphire_dust', '256x alltheores:sapphire_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('diatomite_silked', '128x #c:ores/diatomite', 'minecraft:water', ['512x gtceu:diatomite_dust', '640x gtceu:stone_dust'], ['128x gtceu:hematite_dust', '512x gtceu:hematite_dust', '512x alltheores:sapphire_dust', '512x alltheores:sapphire_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // granitic mineral sand
    process('granitic_mineral_sand', '128x #c:raw_materials/granitic_mineral_sand', 'minecraft:water', ['256x gtceu:granitic_mineral_sand_dust', '256x gtceu:stone_dust'], ['128x gtceu:deepslate_dust', '128x gtceu:stone_dust', '256x gtceu:deepslate_dust', '256x gtceu:magnetite_dust', '256x gtceu:magnetite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('granitic_mineral_sand_silked', '128x #c:ores/granitic_mineral_sand', 'minecraft:water', ['512x gtceu:granitic_mineral_sand_dust', '640x gtceu:stone_dust'], ['128x gtceu:deepslate_dust', '512x gtceu:deepslate_dust', '512x gtceu:magnetite_dust', '512x gtceu:magnetite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // garnet sand
    process('garnet_sand', '128x #c:raw_materials/garnet_sand', 'minecraft:water', ['256x gtceu:garnet_sand_dust', '256x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '128x gtceu:stone_dust', '256x gtceu:red_garnet_dust', '256x gtceu:yellow_garnet_dust', '256x gtceu:yellow_garnet_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('garnet_sand_silked', '128x #c:ores/garnet_sand', 'minecraft:water', ['512x gtceu:garnet_sand_dust', '640x gtceu:stone_dust'], ['128x gtceu:red_garnet_gem', '512x gtceu:red_garnet_dust', '512x gtceu:yellow_garnet_dust', '512x gtceu:yellow_garnet_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // basaltic mineral sand
    process('basaltic_mineral_sand', '128x #c:raw_materials/basaltic_mineral_sand', 'minecraft:water', ['256x gtceu:basaltic_mineral_sand_dust', '256x gtceu:stone_dust'], ['128x gtceu:basalt_dust', '128x gtceu:stone_dust', '256x gtceu:basalt_dust', '256x gtceu:magnetite_dust', '256x gtceu:magnetite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('basaltic_mineral_sand_silked', '128x #c:ores/basaltic_mineral_sand', 'minecraft:water', ['512x gtceu:basaltic_mineral_sand_dust', '640x gtceu:stone_dust'], ['128x gtceu:basalt_dust', '512x gtceu:basalt_dust', '512x gtceu:magnetite_dust', '512x gtceu:magnetite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    
    // fluorite
    process('fluorite', '128x #c:raw_materials/fluorite', 'minecraft:water', ['256x gtceu:fluorite_dust', '256x gtceu:stone_dust'], ['128x gtceu:fluorite_gem', '128x gtceu:stone_dust', '256x gtceu:fluorite_dust', '256x gtceu:fluorite_dust', '256x gtceu:fluorite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('fluorite_silked', '128x #c:ores/fluorite', 'minecraft:water', ['512x gtceu:fluorite_dust', '640x gtceu:stone_dust'], ['128x gtceu:fluorite_gem', '512x gtceu:fluorite_dust', '512x gtceu:fluorite_dust', '512x gtceu:fluorite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
    sift('fluorite', '128x #c:raw_materials/fluorite', 'minecraft:water', ['256x gtceu:stone_dust'], ['128x gtceu:fluorite_gem', '128x gtceu:stone_dust', '256x gtceu:fluorite_dust', '256x gtceu:exquisite_fluorite_gem', '256x gtceu:flawless_fluorite_gem', '256x gtceu:fluorite_gem', '256x gtceu:pure_fluorite_dust'], [2800, 1100, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 1)
    sift('fluorite_silked', '128x #c:ores/fluorite', 'minecraft:water', ['640x gtceu:stone_dust'], ['128x gtceu:fluorite_gem', '512x gtceu:fluorite_dust', '512x gtceu:exquisite_fluorite_gem', '512x gtceu:flawless_fluorite_gem', '512x gtceu:fluorite_gem', '512x gtceu:pure_fluorite_dust'], [2800, 3300, 900, 1900, 6500, 9500], [300, 100, 0, 100, 150, 500, 750], 2)
    
    // dolomite
    process('dolomite', '128x #c:raw_materials/dolomite', 'minecraft:water', ['256x gtceu:dolomite_dust', '256x gtceu:stone_dust'], ['128x gtceu:dolomite_dust', '128x gtceu:stone_dust', '256x gtceu:dolomite_dust', '256x gtceu:dolomite_dust', '256x gtceu:dolomite_dust'], [2800, 1100, 3300, 3300, 6500], [300, 100, 0, 0, 850], 1)
    process('dolomite_silked', '128x #c:ores/dolomite', 'minecraft:water', ['512x gtceu:dolomite_dust', '640x gtceu:stone_dust'], ['128x gtceu:dolomite_dust', '512x gtceu:dolomite_dust', '512x gtceu:dolomite_dust', '512x gtceu:dolomite_dust'], [6500, 3300, 3300, 6500], [850, 0, 0, 850], 2)
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.