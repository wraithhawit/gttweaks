// gttweaks: recipes that duplicate another mod's recipe for the same inputs. Where an All the Mods
// mod (AllTheTweaks, All the Compressed, AllTheOres) has a recipe for the input, its version stays.
// The GregTech ones are ported from ATM9's kubejs/server_scripts/conflicts.js; they exist because
// gtceu.yaml has disableManualCompression: false (as in ATM9), which brings back GregTech's 3x3
// compression. Check a grid with /gttweaks recipematch crafting <grid> on the test server.

ServerEvents.recipes(event => {
    // 3x3 compression that All the Compressed's chain already covers: 9 nether stars / ender pearls
    // -> allthetweaks blocks (the base of allthecompressed:nether_star_block_1x and
    // ender_pearl_block_1x), 9 flint -> allthecompressed:flint_block, and certus quartz, whose
    // compressed chain starts from ae2:quartz_block.
    event.remove({ id: 'gtceu:shaped/block_compress_nether_star' })
    event.remove({ id: 'gtceu:shaped/block_compress_ender_pearl' })
    event.remove({ id: 'gtceu:shaped/block_compress_flint' })
    event.remove({ id: 'gtceu:shaped/block_compress_certus_quartz' })
    // GregTech machines (compressor, fluid solidifier, ...) still make its own blocks of these; point
    // every recipe that outputs one at the block the crafting grid gives instead. GregTech recipes
    // are rewritten through the { mod: 'gtceu' } filter, the one gtceu_output_unification.js relies
    // on; an { output: ... } filter alone left the compressor and fluid solidifier unchanged. The
    // counts are logged so the server log shows what each filter matched.
    const blockOutputs = {
        'gtceu:nether_star_block': 'allthetweaks:nether_star_block',
        'gtceu:ender_pearl_block': 'allthetweaks:ender_pearl_block',
        'gtceu:flint_block': 'allthecompressed:flint_block',
        'gtceu:certus_quartz_block': 'ae2:quartz_block'
    }
    Object.keys(blockOutputs).forEach(from => {
        console.info(`[gttweaks] ${from} outputs before rewrite: output filter ${event.countRecipes({ output: from })}, `
            + `gtceu output filter ${event.countRecipes({ mod: 'gtceu', output: from })}`)
        event.replaceOutput({ mod: 'gtceu' }, from, blockOutputs[from])
        event.replaceOutput({ output: from }, from, blockOutputs[from])
    })

    // One block back to items: GregTech's shapeless decompress takes #c:storage_blocks/<material>,
    // so an AllTheTweaks nether star or ender pearl block matched both it and AllTheTweaks' own
    // recipe, and an AE2 quartz block gave GregTech certus gems instead of AE2 crystals. The
    // AllTheTweaks recipes read the same tags, so leftover GregTech blocks still decompress there.
    // GregTech's flint decompress only takes its own block and stays for leftovers.
    event.remove({ id: 'gtceu:shapeless/block_decompress_nether_star' })
    event.remove({ id: 'gtceu:shapeless/block_decompress_ender_pearl' })
    event.remove({ id: 'gtceu:shapeless/block_decompress_certus_quartz' })

    // GregTech machine recipes that only accepted the GregTech block, so the blocks players now get
    // could not go in (found with /gttweaks recipematch input). The forge hammer, cutter and
    // extractor versions already read the storage block tags. All the Compressed's flint block is
    // in no storage block tag, so the flint recipes take it by item.
    event.replaceInput({ id: 'gtceu:macerator/macerate_nether_star_block' }, 'gtceu:nether_star_block', '#c:storage_blocks/nether_star')
    event.replaceInput({ id: 'gtceu:macerator/macerate_ender_pearl_block' }, 'gtceu:ender_pearl_block', '#c:storage_blocks/ender_pearl')
    event.replaceInput({ id: 'gtceu:macerator/macerate_certus_quartz_block' }, 'gtceu:certus_quartz_block', '#c:storage_blocks/certus_quartz')
    event.replaceInput({ id: 'gtceu:macerator/macerate_flint_block' }, 'gtceu:flint_block', 'allthecompressed:flint_block')
    event.replaceInput({ id: 'gtceu:forge_hammer/hammer_flint_block_to_gem' }, 'gtceu:flint_block', 'allthecompressed:flint_block')

    // Let GregTech certus gems still make the AE2 block, as AE2's 2x2 does for its own crystal.
    event.shaped('ae2:quartz_block', ['CC', 'CC'], { C: 'gtceu:certus_quartz_gem' })
        .id('gttweaks:ae2/quartz_block_from_gt_certus')

    // 9 flint also matched Supplementaries' flint block; All the Compressed's flint_block is the base of
    // its flint chain, so that one stays. Supplementaries' block -> 9 flint recipe is left in place.
    event.remove({ id: 'supplementaries:flint_block' })

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
