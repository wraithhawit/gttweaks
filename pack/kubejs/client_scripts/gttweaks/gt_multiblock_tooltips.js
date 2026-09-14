// gttweaks: tooltips for the pack's custom GregTech multiblocks, ported word for word from ATM9's
// kubejs/client_scripts/tooltips.js. The machines are registered in startup_scripts/gtceu/.
//
// ATM9's circuit tooltips ("Your first LV circuit." plus a tier line) are not ported: GregTech
// already gives all 28 circuits a description and a tier line, so ATM9's added a second tier line.
// Client scripts do not reload with /reload; use /kubejs reload client_scripts.

ItemEvents.modifyTooltips(event => {
    event.add('gtceu:neocube', [Text.of('Not an AssLine... A NeoCube')])
    event.add('gtceu:neural_node', [Text.of('Self-Aware')])
    event.add('gtceu:advanced_large_chemical_reactor', [Text.of('ALCR a LCR that can parallelize.')])
})
