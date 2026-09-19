// gttweaks: crafting recipes for the Refined Storage infinite disks, mirroring ATM10's own infinity
// cell recipes (server_scripts/mods/ExtendedAE/InfinityCellRecipes.js) with Refined Storage parts:
//   ae2:quartz_glass               -> refinedstorage:quartz_enriched_iron
//   megacells:cell_component_256m  -> extradisks:262144k_item_storage_part (262m), or the 262kB fluid
//                                     part for the lava disk
//   extendedae:infinity_cobblestone_cell -> rsadditionaldisks:infinite_cobblestone_disk, and the gravel
//                                     cell in the sand recipe -> the gravel disk
// The compressed block, the two side ingredients and the ATM alloy ingots are unchanged.

ServerEvents.recipes(event => {
    if (!Platform.isLoaded('rsadditionaldisks') || !Platform.isLoaded('extradisks')) return

    const ITEM_PART = 'extradisks:262144k_item_storage_part'
    const FLUID_PART = 'extradisks:262144b_fluid_storage_part'

    function disk(output, item, typeA, typeB, component) {
        event.shaped(output, [
            'GIG',
            'XCY',
            'AVU'
        ], {
            G: 'refinedstorage:quartz_enriched_iron',
            I: item,
            X: typeA,
            C: component,
            Y: typeB,
            A: 'allthemodium:unobtainium_allthemodium_alloy_ingot',
            V: 'allthemodium:unobtainium_vibranium_alloy_ingot',
            U: 'allthemodium:vibranium_allthemodium_alloy_ingot'
        }).id(`gttweaks:rsadditionaldisks/${output.split(':')[1]}`)
    }

    disk('kubejs:lava_disk', 'allthecompressed:blazing_crystal_block_3x', 'minecraft:lava_bucket', 'minecraft:lava_bucket', FLUID_PART)
    disk('kubejs:dirt_disk', 'allthecompressed:dirt_6x', 'minecraft:water_bucket', 'minecraft:mud', ITEM_PART)
    disk('kubejs:moss_disk', 'allthecompressed:moss_block_5x', 'minecraft:bone_block', 'minecraft:bone_block', ITEM_PART)
    disk('kubejs:andesite_disk', 'allthecompressed:andesite_5x', 'minecraft:water_bucket', 'minecraft:lava_bucket', ITEM_PART)
    disk('kubejs:diorite_disk', 'allthecompressed:diorite_5x', 'minecraft:water_bucket', 'minecraft:lava_bucket', ITEM_PART)
    disk('kubejs:granite_disk', 'allthecompressed:granite_5x', 'minecraft:water_bucket', 'minecraft:lava_bucket', ITEM_PART)
    disk('kubejs:tuff_disk', 'allthecompressed:tuff_5x', 'minecraft:water_bucket', 'minecraft:lava_bucket', ITEM_PART)
    disk('kubejs:gravel_disk', 'allthecompressed:gravel_6x', 'mekanism:crusher', 'mekanism:crusher', 'rsadditionaldisks:infinite_cobblestone_disk')
    disk('kubejs:sand_disk', 'allthecompressed:sand_6x', 'mekanism:crusher', 'mekanism:crusher', 'kubejs:gravel_disk')
    disk('kubejs:red_sand_disk', 'allthecompressed:red_sand_5x', 'minecraft:lava_bucket', 'minecraft:lava_bucket', ITEM_PART)
    disk('kubejs:end_stone_disk', 'allthecompressed:end_stone_6x', 'minecraft:purpur_block', 'minecraft:purpur_block', ITEM_PART)
    disk('kubejs:netherrack_disk', 'allthecompressed:netherrack_6x', 'minecraft:lava_bucket', 'minecraft:gravel', ITEM_PART)
    disk('kubejs:clay_disk', 'allthecompressed:clay_5x', 'minecraft:mud', 'minecraft:mud', ITEM_PART)
    disk('kubejs:blackstone_disk', 'allthecompressed:blackstone_6x', 'minecraft:lava_bucket', 'minecraft:blue_ice', ITEM_PART)
    disk('kubejs:basalt_disk', 'allthecompressed:basalt_6x', 'minecraft:lava_bucket', 'minecraft:blue_ice', ITEM_PART)
    disk('kubejs:calcite_disk', 'allthecompressed:calcite_5x', 'minecraft:water_bucket', 'minecraft:bone_block', ITEM_PART)
    disk('kubejs:cobbled_deepslate_disk', 'allthecompressed:cobbled_deepslate_6x', 'minecraft:lava_bucket', 'minecraft:packed_ice', ITEM_PART)
    disk('kubejs:soul_sand_disk', 'allthecompressed:soul_sand_6x', 'allthemodium:soul_lava_bucket', 'minecraft:sand', ITEM_PART)
    disk('kubejs:sky_stone_disk', 'allthecompressed:sky_stone_block_5x', 'allthemodium:soul_lava_bucket', 'ae2:sky_dust', ITEM_PART)
    disk('kubejs:kivi_disk', 'allthecompressed:kivi_5x', 'allthemodium:soul_lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:ancient_stone_disk', 'allthecompressed:ancient_stone_5x', 'allthemodium:soul_lava_bucket', 'minecraft:lava_bucket', ITEM_PART)

    disk('kubejs:white_disk', 'allthecompressed:white_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:light_gray_disk', 'allthecompressed:light_gray_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:gray_disk', 'allthecompressed:gray_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:black_disk', 'allthecompressed:black_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:brown_disk', 'allthecompressed:brown_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:red_disk', 'allthecompressed:red_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:orange_disk', 'allthecompressed:orange_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:yellow_disk', 'allthecompressed:yellow_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:green_disk', 'allthecompressed:green_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:lime_disk', 'allthecompressed:lime_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:cyan_disk', 'allthecompressed:cyan_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:light_blue_disk', 'allthecompressed:light_blue_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:blue_disk', 'allthecompressed:blue_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:purple_disk', 'allthecompressed:purple_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:magenta_disk', 'allthecompressed:magenta_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
    disk('kubejs:pink_disk', 'allthecompressed:pink_concrete_6x', 'minecraft:lava_bucket', 'minecraft:water_bucket', ITEM_PART)
})
