// gttweaks: GregTech spells it aluminium, AllTheOres spells it aluminum, so the two never share a tag.
// config/almostunified/tags.json substitutes each c:<form>/aluminium tag with c:<form>/aluminum, as
// ATM9's unify.json tagOwnerships did. AlmostUnified only goes one way: it adds GregTech's items to
// the aluminum tag and swaps the tag in the recipes it unifies. It never loads a gtceu machine recipe
// (see gtceu_output_unification.js), so those still ask for #c:<form>/aluminium - while that script
// now turns their aluminium outputs into AllTheOres items. Putting the AllTheOres items into the
// aluminium tags too lets them feed back into every GregTech aluminium recipe.

ServerEvents.tags('item', event => {
    const forms = {
        'c:ingots/aluminium': 'alltheores:aluminum_ingot',
        'c:plates/aluminium': 'alltheores:aluminum_plate',
        'c:rods/aluminium': 'alltheores:aluminum_rod',
        'c:gears/aluminium': 'alltheores:aluminum_gear',
        'c:dusts/aluminium': 'alltheores:aluminum_dust',
        'c:storage_blocks/aluminium': 'alltheores:aluminum_block',
        'c:raw_materials/aluminium': 'alltheores:raw_aluminum',
        'c:storage_blocks/raw_aluminium': 'alltheores:raw_aluminum_block'
    }
    Object.keys(forms).forEach(tag => event.add(tag, forms[tag]))
})
