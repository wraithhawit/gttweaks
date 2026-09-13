// Support items the ported GregTech recipe scripts need that ATM10 does not already register.
// Original definitions by AllTheMods Staff / community contributors, from ATM9.
//
// WHY THIS FILE EXISTS. The ATM9 -> ATM10 port copied `startup_scripts/gtceu/` and stopped there,
// which looked complete because every multiblock formed. It was not: the GregTech *recipe* scripts
// use custom KubeJS items registered elsewhere in ATM9's startup tree, and gtceu.js died on the
// first one ("Failed to read ingredient from kubejs:inert_nether_star").
//
// SCOPE CHECK BEFORE ADDING ANYTHING HERE. ATM10 already ships most of them under its own names:
//   magical_soil    -> startup_scripts/CustomAdditions.js
//   universal_press -> startup_scripts/Applied Energistics/Universal_Press.js
// Registering those again is a hard error ("Duplicate key 'kubejs:magical_soil'") that fails the
// whole StartupEvents.registry callback. Grep the ENTIRE startup_scripts tree, not just gtceu/,
// before adding a registration — checking only this folder is what caused that error once already.
//
// NOT items at all, so not registered here: `kubejs:inert_star` and `kubejs:inert_fluid` are
// RECIPE IDS — the first argument to chemical_bath(...) and mixer(...).

StartupEvents.registry('item', allthemods => {
    // ATM9 startup_scripts/custom_additions.js. Texture is vanilla's nether star, as in ATM9.
    allthemods.create('inert_nether_star')
        .displayName('Inert Nether Star')
        .texture('minecraft:item/nether_star')
        .tooltip('Needs activating...')

    // ATM9 startup_scripts/AE2/Universal_Press.js. ATM10 ships `universal_press` already but not
    // these two, so only the missing pair is created.
    allthemods.create('universal_addon_press')
        .texture('kubejs:item/universal_addon_press')
        .displayName('Universal Addon Press')

    allthemods.create('ultimate_universal_press')
        .texture('kubejs:item/ultimate_universal_press')
        .displayName('Ultimate Universal Press')
})
