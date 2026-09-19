// Ported from All the Mods 9's GregTech starforge recipes, which produced
// pre-filled AE2 infinity cells as `Item.of('expatternprovider:infinity_cell',
// '{record: {...}}')` — one generic item carrying its contents in NBT.
//
// That form does not exist any more. Extended AE renamed its modid to
// `extendedae` AND changed how the cell stores its contents: ItemInfinityCell
// now holds `private AEKey record` as a field on the *item*, set at
// registration, rather than as per-stack data. So each cell has to be its own
// registered item — which is exactly what ATM10 already does for its own 37
// cells in ExtendedAE/InfinityCells.js. This file follows that same pattern for
// the GregTech fluids the ATM9 recipes wanted.
//
// Each cell has its own item texture, drive texture and drive model under
// kubejs:item/<fluid>_cell and kubejs:block/drive/<fluid>_cell, made the way
// ATM10 colours its own cells: ATM10's lava cell textures with lava's three
// accent shades swapped for a ramp of the fluid's colour (mid = the colour,
// dark = 0.6x, light = 1.5x capped). The colour is the fluid texture's average
// multiplied by the GregTech material colour, which is how GregTech tints it.
//
// minecraft:lava is deliberately absent — ATM10 already registers kubejs:lava_cell.
// The two Mekanism *chemical* cells from ATM9 (polonium, plutonium) are not here
// either: InfinityCellBuilder exposes fluidType/itemType but no chemical variant,
// so they need MekanismKey.of(ChemicalStack) through the generic type(Supplier)
// overload. Left out until the fluid cells are confirmed working.

StartupEvents.registry('item', allthemods => {

    const gtFluidCells = [
        'chlorine',
        'sulfuric_acid',
        'hydrogen',
        'oxygen',
        'styrene_butadiene_rubber',
        'polytetrafluoroethylene',
        'polybenzimidazole',
        'ethylene',
        'lubricant',
        'air',
        'nether_air',
        'ender_air',
        'fluorine',
        'soldering_alloy'
    ]

    gtFluidCells.forEach(fluid => {
        allthemods.create(`${fluid}_cell`, 'custom_infinity_cell')
            .texture(`kubejs:item/${fluid}_cell`)
            .fluidType(`gtceu:${fluid}`)
            .cellModel(`kubejs:block/drive/${fluid}_cell`)
    })
})
