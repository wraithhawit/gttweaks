// gttweaks: GregTech recipes that duplicate another mod's recipe for the same inputs, ported from
// ATM9's kubejs/server_scripts/conflicts.js. They exist because gtceu.yaml has
// disableManualCompression: false (as in ATM9), which brings back GregTech's 3x3 compression.
// Every id below was confirmed loading on the ATM10(3) test server with the recipe probe.

ServerEvents.recipes(event => {
    // 3x3 compression that All the Compressed's chain already covers: 9 nether stars / ender pearls
    // -> allthetweaks blocks (the base of allthecompressed:nether_star_block_1x and
    // ender_pearl_block_1x), 9 flint -> allthecompressed:flint_block, and certus quartz, whose
    // compressed chain starts from ae2:quartz_block.
    event.remove({ id: 'gtceu:shaped/block_compress_nether_star' })
    event.remove({ id: 'gtceu:shaped/block_compress_ender_pearl' })
    event.remove({ id: 'gtceu:shaped/block_compress_flint' })
    event.remove({ id: 'gtceu:shaped/block_compress_certus_quartz' })
    // Let GregTech certus gems still make the AE2 block, as AE2's 2x2 does for its own crystal.
    event.shaped('ae2:quartz_block', ['CC', 'CC'], { C: 'gtceu:certus_quartz_gem' })
        .id('gttweaks:ae2/quartz_block_from_gt_certus')

    // Hand-mixed brass dust, the same 3 copper + 1 zinc -> 4 as AllTheOres'
    // crafting/brass/alloy_blending_from_dust. (GregTech's bronze version already does not load.)
    event.remove({ id: 'gtceu:shapeless/dust_brass' })

    // Raw uraninite conflicts with Powah's uraninite: smelt GregTech's raw ore to its dust instead
    // of an "ingot", and keep the raw storage block recipe under our own id.
    event.remove({ id: 'gtceu:smelting/smelt_raw_uraninite_ore_to_ingot' })
    event.remove({ id: 'gtceu:shaped/compress_uraninite_to_ore_block' })
    event.smelting('gtceu:uraninite_dust', 'gtceu:raw_uraninite')
        .id('gttweaks:gtceu/smelting_raw_uraninite_to_dust')
    event.blasting('gtceu:uraninite_dust', 'gtceu:raw_uraninite')
        .id('gttweaks:gtceu/blasting_raw_uraninite_to_dust')
    event.shapeless('gtceu:raw_uraninite_block', [
        'gtceu:raw_uraninite', 'gtceu:raw_uraninite', 'gtceu:raw_uraninite',
        'gtceu:raw_uraninite', 'gtceu:raw_uraninite', 'gtceu:raw_uraninite',
        'gtceu:raw_uraninite', 'gtceu:raw_uraninite', 'gtceu:raw_uraninite'
    ]).id('gttweaks:gtceu/raw_uraninite_block')
})
