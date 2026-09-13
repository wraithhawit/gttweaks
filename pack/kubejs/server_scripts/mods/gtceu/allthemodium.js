// Ported from All the Mods 9 to GTCEu 8 + KubeJS 2101. Two changes:
//
//   .notConsumable(itemId) -> .notConsumableItem(itemId)
//       GTCEu 8 reserves notConsumable for the (TagPrefix, Material) form; the item form was split.
//   .notConsumable(Item.of('gtceu:programmed_circuit', '{Configuration:1}').strongNBT()) -> .circuit(1)
//       Both halves of that expression died on 1.21: NBT strings became data components (the tag is
//       now the gtceu:circuit_config component) and KubeJS 2101 dropped strongNBT(). Rebuilding the
//       stack by hand would work, but .circuit(int) is a first-class method on the GT recipe builder
//       and says what the recipe means instead of how the item is encoded.

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

ServerEvents.recipes(allthemods => {
        for (const type of ['unobtainium', 'vibranium', 'allthemodium']) {
        allthemods.recipes.gtceu.lathe('gtceu:lathe_'+type+'_to_rod')
                .itemInputs('allthemodium:'+type+'_ingot')
                .itemOutputs('2x allthemodium:'+type+'_rod')
                .duration(400)
                .EUt(16);

        allthemods.recipes.gtceu.extruder('gtceu:extrude_'+type+'_to_rod')
                .itemInputs('allthemodium:'+type+'_ingot')
                .notConsumableItem('gtceu:rod_extruder_mold')
                .itemOutputs('2x allthemodium:'+type+'_rod')
                .duration(400)
                .EUt(40);

        allthemods.recipes.gtceu.extruder('gtceu:extrude_'+type+'_to_gear')
                .itemInputs('4x allthemodium:'+type+'_ingot')
                .notConsumableItem('gtceu:gear_extruder_mold')
                .itemOutputs('allthemodium:'+type+'_gear')
                .duration(960)
                .EUt(180);

        allthemods.recipes.gtceu.extruder('gtceu:extrude_'+type+'_to_plate')
                .itemInputs('allthemodium:'+type+'_ingot')
                .notConsumableItem('gtceu:plate_extruder_mold')
                .itemOutputs('allthemodium:'+type+'_plate')
                .duration(192)
                .EUt(240);

        allthemods.recipes.gtceu.bender('gtceu:bend_'+type+'_to_plate')
                .circuit(1)
                .itemInputs('allthemodium:'+type+'_ingot')
                .itemOutputs('allthemodium:'+type+'_plate')
                .duration(192)
                .EUt(24);
    }
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
