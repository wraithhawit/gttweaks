// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

GTCEuStartupEvents.registry('gtceu:recipe_type', allthemods => {
    allthemods.create('mega_chemical_reactor')
        .category('gregstar')
        .setEUIO('in')
        .setMaxIOSize(3, 3, 5, 4)
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE)
        .setSound(GTSoundEntries.CHEMICAL);

    GTRecipeTypes.LARGE_CHEMICAL_RECIPES.onRecipeBuild((builder, provider) => {
        GTRecipeTypes.get('mega_chemical_reactor').copyFrom(builder)
            .duration(Math.max((builder.duration / 2), 1))
            .save(provider);
    });

    GTRecipeTypes.CHEMICAL_RECIPES.onRecipeBuild((builder, provider) => {
        GTRecipeTypes.get('mega_chemical_reactor').copyFrom(builder)
            .duration(Math.max((builder.duration / 2), 1))
            .save(provider);

        GTRecipeTypes.LARGE_CHEMICAL_RECIPES.copyFrom(builder).save(provider);
    });
})

GTCEuStartupEvents.registry('gtceu:machine', allthemods => {
    allthemods.create('advanced_large_chemical_reactor', 'multiblock')
        .rotationState(RotationState.NON_Y_AXIS)
        .appearanceBlock(GTBlocks.CASING_PTFE_INERT)
        .recipeTypes('mega_chemical_reactor')
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.OC_PERFECT_SUBTICK])
        .pattern(definition => FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice('c   c', 'ccccc', 'c   c', 'ccccc', 'c   c')
            .slice('ccccc', 'cpppc', 'cwwwc', 'cpppc', 'ccccc')
            .slice('c   c', 'cwwwc', 'cpppc', 'cwwwc', 'c   c')
            .slice('ccccc', 'cpppc', 'cwwwc', 'cpppc', 'ccccc')
            .slice('c   c', 'kcccc', 'c   c', 'ccccc', 'c   c')
            .where('k', Predicates.controller(Predicates.blocks(definition.get())))
            // .where('w', Predicates.blocks(GTBlocks.COIL_RTMALLOY.get())
            .where('w', Predicates.heatingCoils()
                .or(Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
            )
            .where('p', Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get())
                .or(Predicates.heatingCoils())
                // .or(Predicates.blocks(GTBlocks.COIL_RTMALLOY.get()))
            )
            .where(' ', Predicates.any())
            .where('c', Predicates.blocks(GTBlocks.CASING_PTFE_INERT.get())
                .or(Predicates['autoAbilities(com.gregtechceu.gtceu.api.recipe.GTRecipeType[])'](Array.from(definition.getRecipeTypes())))
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.MAINTENANCE).setExactLimit(1))
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
            )
        .build())
        .workableCasingModel(
            "gtceu:block/casings/solid/machine_casing_inert_ptfe",
            "gtceu:block/multiblock/large_chemical_reactor"
        )
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
