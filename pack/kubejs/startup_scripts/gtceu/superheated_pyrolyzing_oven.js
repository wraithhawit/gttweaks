GTCEuStartupEvents.registry('gtceu:recipe_type', allthemods => {
  allthemods.create('superheated_pyrolyzing_oven')
    .category('dim')
    .setEUIO('in')
    .setMaxIOSize(2, 1, 1, 1) 
    .setItemSlotOverlay(IO.IN, 0, GTGuiTextures.SOLIDIFIER_OVERLAY)
    .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
    .setSound(GTSoundEntries.CHEMICAL);
});

GTCEuStartupEvents.registry('gtceu:machine', allthemods => {
    allthemods.create("superheated_pyrolyzing_oven", "multiblock")
        .rotationState(RotationState.NON_Y_AXIS)
        .blockEntityFactory((holder) => new CoilWorkableElectricMultiblockMachine(holder))
        .recipeTypes('superheated_pyrolyzing_oven')
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.BATCH_MODE, (machine, recipe) => GTRecipeModifiers.pyrolyseOvenOverclock(machine, recipe)])
        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
        .pattern(definition => FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("BBCCCBB", "BBCDCBB", "BBCCCBB", "BBCCCBB", "BBEEEBB", "BBEEEBB")
            .slice("BCCCCCB", "BCAFACB", "BCAFACB", "BCGGGCB", "BEAAAEB", "BEHHHEB")
            .slice("CCCCCCC", "CAAFAAC", "CAAFAAC", "CGGGGGC", "EAAAAAE", "EHHHHHE")
            .slice("CCCCCCC", "DFFFFFD", "CFFFFFC", "CGGGGGC", "EAAAAAE", "EHHMHHE")
            .slice("CCCCCCC", "CAAFAAC", "CAAFAAC", "CGGGGGC", "EAAAAAE", "EHHHHHE")
            .slice("BCCCCCB", "BCAFACB", "BCAFACB", "BCGGGCB", "BEAAAEB", "BEHHHEB")
            .slice("BBCCCBB", "BBCICBB", "BBCCCBB", "BBCCCBB", "BBEEEBB", "BBEEEBB")
            .where("A", Predicates.blocks("minecraft:air"))
            .where("B", Predicates.any())
            .where('C', Predicates.blocks('gtceu:solid_machine_casing').setMinGlobalLimited(10) 
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.MAINTENANCE).setExactLimit(1))
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
                .or(Predicates['autoAbilities(com.gregtechceu.gtceu.api.recipe.GTRecipeType[])'](Array.from(definition.getRecipeTypes()))))
            .where("D", Predicates.blocks("gtceu:steel_firebox_casing"))
            .where("E", Predicates.blocks("gtceu:laminated_glass"))
            .where("F", Predicates.blocks("gtceu:ptfe_pipe_casing"))
            .where("G", Predicates.heatingCoils())
            .where("H", Predicates.blocks("gtceu:high_temperature_smelting_casing"))
            .where("M", Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.MUFFLER).setExactLimit(1))
            .where('I', Predicates.controller(Predicates.blocks(definition.get())))
            .build())
        .workableCasingModel("gtceu:block/casings/solid/machine_casing_solid_steel",
            "gtceu:block/multiblock/pyrolyse_oven");

})
