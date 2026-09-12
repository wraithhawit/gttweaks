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
// Art is reused rather than drawn: `extendedae:item/infinity_cell` is Extended
// AE's own neutral cell texture, and every cell shares one drive model
// (kubejs:block/drive/gt_cell) pointing at their neutral drive texture. The
// cells are therefore correct and readable but not individually coloured.
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
            .texture('extendedae:item/infinity_cell')
            .fluidType(`gtceu:${fluid}`)
            .cellModel('kubejs:block/drive/gt_cell')
    })
})
