// gttweaks: Ad Astra recipe changes ported from ATM9's kubejs/server_scripts/mods/ad_astra/ad_astra.js,
// now that Ad Astra is in ATM10. The desh/ostrum/calorite bender recipes live in mods/gtceu/gtceu.js.
//
// ATM9's "oil is oil" refining recipe (#forge:crude_oil -> ad_astra:fuel) is not ported: Ad Astra 1.21
// ships refining/fuel_from_refining_oil, whose #ad_astra:oil tag already includes #c:oil and
// #c:crude_oil. ATM9's removal of ad_astra:recipes/steel_ingot_from_blasting_iron_ingot has no 1.21
// counterpart; Ad Astra no longer blasts iron into steel.

ServerEvents.recipes(event => {
    // Iron ingot + coal -> steel in the etrionic blast furnace: steel long before GregTech's own
    // steel, and it feeds GregTech through #c:ingots/steel. ATM9 removed the whole recipe type; in
    // 1.21 this is its only recipe.
    event.remove({ type: 'ad_astra:alloying' })

    // ATM9 removed the etrionic blast furnace recipe as a dupe exploit. Not re-checked against
    // Ad Astra 1.16.19; no ATM10 quest uses the machine.
    event.remove({ id: 'ad_astra:etrionic_blast_furnace' })
})
