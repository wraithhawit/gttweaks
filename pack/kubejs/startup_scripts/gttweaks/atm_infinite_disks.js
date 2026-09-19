// gttweaks: Refined Storage equivalents of ATM10's own infinity cells (startup_scripts/ExtendedAE/
// InfinityCells.js), one infinite disk per cell, registered with the 'infinite_disk' item type from the
// Refined Storage - Additional Disks mod. Needs nothing from GregTech, so it also runs in ATM10 copies
// without it; the GregTech fluid disks are in gtceu/gt_infinite_disks.js.
//
// Icons are Refined Storage's 64B fluid disk with the fluid panel tinted to each ATM10 cell's colour
// and the creative disk's white tier pixel. Recipes: server_scripts/gttweaks/infinite_disk_recipes.js.

if (Platform.isLoaded('rsadditionaldisks')) {
    StartupEvents.registry('item', event => {
        const disks = {
            dirt: 'minecraft:dirt',
            moss: 'minecraft:moss_block',
            andesite: 'minecraft:andesite',
            diorite: 'minecraft:diorite',
            granite: 'minecraft:granite',
            tuff: 'minecraft:tuff',
            gravel: 'minecraft:gravel',
            sand: 'minecraft:sand',
            red_sand: 'minecraft:red_sand',
            end_stone: 'minecraft:end_stone',
            netherrack: 'minecraft:netherrack',
            clay: 'minecraft:clay',
            blackstone: 'minecraft:blackstone',
            basalt: 'minecraft:basalt',
            calcite: 'minecraft:calcite',
            cobbled_deepslate: 'minecraft:cobbled_deepslate',
            soul_sand: 'minecraft:soul_sand',
            sky_stone: 'ae2:sky_stone_block',
            kivi: 'xycraft_world:kivi',
            ancient_stone: 'allthemodium:ancient_stone',
            white: 'minecraft:white_dye',
            light_gray: 'minecraft:light_gray_dye',
            gray: 'minecraft:gray_dye',
            black: 'minecraft:black_dye',
            brown: 'minecraft:brown_dye',
            red: 'minecraft:red_dye',
            orange: 'minecraft:orange_dye',
            yellow: 'minecraft:yellow_dye',
            lime: 'minecraft:lime_dye',
            green: 'minecraft:green_dye',
            cyan: 'minecraft:cyan_dye',
            light_blue: 'minecraft:light_blue_dye',
            blue: 'minecraft:blue_dye',
            purple: 'minecraft:purple_dye',
            magenta: 'minecraft:magenta_dye',
            pink: 'minecraft:pink_dye'
        }

        Object.keys(disks).forEach(name => {
            event.create(`${name}_disk`, 'infinite_disk')
                .itemType(disks[name])
                .texture(`kubejs:item/${name}_disk`)
        })

        // ATM10's one fluid cell.
        event.create('lava_disk', 'infinite_disk')
            .fluidType('minecraft:lava')
            .texture('kubejs:item/lava_disk')
    })
}
