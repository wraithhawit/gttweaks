// Ported from All the Mods 9 for GregTech 8.0.0-SNAPSHOT / KubeJS 2101.7.2.
// Change from the ATM9 original: .machine(holder => ...) became
// .blockEntityFactory(info => ...) — 8.0 renamed the builder method and the
// callback argument is now BlockEntityCreationInfo. Everything else is verbatim.

const CoilWorkableElectricMultiblockMachine = Java.loadClass("com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine")
GTCEuStartupEvents.registry('gtceu:machine', allthemods => {
allthemods.create("highly_reactive_molten_blast_smeltery", "multiblock")
        .blockEntityFactory((info) => new CoilWorkableElectricMultiblockMachine(info))
        .rotationState(RotationState.ALL)
        .recipeTypes(GTRecipeTypes.get('alloy_blast_smelter'))
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.BATCH_MODE, (machine, recipe) => GTRecipeModifiers.ebfOverclock(machine, recipe)])
        .appearanceBlock(GCYMBlocks.CASING_HIGH_TEMPERATURE_SMELTING)
        .pattern(definition => FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("BCCCB", "BCDCB", "BCDCB", "BEEEB", "BFFFB", "BCCCB", "BBBBB", "BBBBB", "BBBBB")
            .slice("CCCCC", "CDGDC", "CADAC", "EADAE", "FAFAF", "CCCCC", "BCFCB", "BBFBB", "BBFBB")
            .slice("CCCCC", "DGHGD", "DDHDD", "EDHDE", "FFAFF", "CCCCC", "BFCFB", "BFAFB", "BFIFB")
            .slice("CCCCC", "CDGDC", "CADAC", "EADAE", "FAFAF", "CCCCC", "BCFCB", "BBFBB", "BBFBB")
            .slice("BCCCB", "BCJCB", "BCDCB", "BEEEB", "BFFFB", "BCCCB", "BBBBB", "BBBBB", "BBBBB")
            .where("A", Predicates.blocks("minecraft:air"))
            .where('B', Predicates.any())
           .where('C', Predicates.blocks('gtceu:high_temperature_smelting_casing').setMinGlobalLimited(10) 
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.MAINTENANCE).setExactLimit(1))
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
                .or(Predicates['autoAbilities(com.gregtechceu.gtceu.api.recipe.GTRecipeType[])'](Array.from(definition.getRecipeTypes()))))
            .where("D", Predicates.blocks("gtceu:neutronium_frame"))
            .where("E", Predicates.blocks("gtceu:heat_vent"))
            .where("F", Predicates.heatingCoils())
            .where("G", Predicates.blocks("gtceu:fusion_coil"))
            .where("H", Predicates.blocks("gtceu:robust_machine_casing"))
            .where("I", Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.MUFFLER).setExactLimit(1))
            .where('J', Predicates.controller(Predicates.blocks(definition.get())))
            .build())
        .workableCasingModel("gtceu:block/casings/gcym/high_temperature_smelting_casing", 'gtceu:block/multiblock/gcym/blast_alloy_smelter');
})
