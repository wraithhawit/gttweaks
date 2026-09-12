// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

const $FusionReactorMachine = Java.loadClass("com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine")

GTCEuStartupEvents.registry('gtceu:recipe_type', allthemods => {
    allthemods.create('mega_fusion_reactor')
        .category('gregstar')
        .setEUIO('in')
        .setMaxIOSize(2, 2, 4, 2)
        .setProgressBar(GTGuiTextures.PROGRESS_FUSION)
        .setSound(GTSoundEntries.ARC);

    GTRecipeTypes.FUSION_RECIPES.onRecipeBuild((builder, provider) => {
        GTRecipeTypes.get('mega_fusion_reactor').copyFrom(builder)
            .duration(Math.max((builder.duration / 2), 1))
            .EUt(builder.EUt().voltage(), builder.EUt().amperage() * 1.5)
            .save(provider);
    });
})

GTCEuStartupEvents.registry('gtceu:machine', allthemods => {
    allthemods.create('mega_fusion_reactor', 'multiblock').blockEntityFactory((holder) => new $FusionReactorMachine(holder, GTValues.UIV))
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeTypes(GTRecipeTypes.get('mega_fusion_reactor'))
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.OC_NON_PERFECT])
        .appearanceBlock(GCYMBlocks.CASING_ATOMIC)
        .pattern(definition => FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("                                 ", "                                 ", "C  N C                     C N  C", "C  N C                     C N  C", "C  N C                     C N  C", "C  N C                     C N  C", "                                 ", "                                 ")
            .slice("                                 ", "C  N C        AAAAA        C N  C", "ATT#H#H C    CAAAAAC    C H#H#TTA", "ATT#H#H CS  SCAGGGACS  SC H#H#TTA", "ATT#H#H CS  SCAGGGACS  SC H#H#TTA", "ATT#H#H C    CAAAAAC    C H#H#TTA", "C  N C        AAAAA        C N  C", "                                 ")
            .slice("C  N C                     C N  C", "ATT#HC  C    CAAAAAC    C  CH#TTA", "A#####H C    C#####C    C H#####A", "A#####HH#CAACC#####CCAAC#HH#####A", "A#####HH#CAACC#####CCAAC#HH#####A", "A#####H C    C#####C    C H#####A", "ATT#HC  C    CAAAAAC    C  CH#TTA", "C  N C                     C N  C")
            .slice("C  N C                     C N  C", "ATT#H#H CS  SCAAAAACS  SC H#H#TTA", "A######H#CAAC#######CAAC#H######A", "G###############################G", "G###############################G", "A######H#CAAC#######CAAC#H######A", "ATT#H#H CS  SCAAAAACS  SC H#H#TTA", "C  N C                     C N  C")
            .slice("C  N C                     C N  C", "ATT#H#H CS  SCAAAAACS  SC H#H#TTA", "A######H#CAAC#######CAAC#H######A", "G###############################G", "G###############################G", "A######H#CAAC#######CAAC#H######A", "ATT#H#H CS  SCAAAAACS  SC H#H#TTA", "C  N C                     C N  C")
            .slice("C  N C                     C N  C", "ATT#HC  C    CAAAAAC    C  CH#TTA", "A#####H C    C#####C    C H#####A", "A#####HH#CAACC#####CCAAC#HH#####A", "A#####HH#CAACC#####CCAAC#HH#####A", "A#####H C    C#####C    C H#####A", "ATT#HC  C    CAAAAAC    C  CH#TTA", "C  N C                     C N  C")
            .slice("                                 ", "C  N C        AAAAA        C N  C", "ATT#H#H C    CAAMAAC    C H#H#TTA", "ATT#H#H CS  SCAGGGACS  SC H#H#TTA", "ATT#H#H CS  SCAGGGACS  SC H#H#TTA", "ATT#H#H C    CAAAAAC    C H#H#TTA", "C  N C        AAAAA        C N  C", "                                 ")
            .slice("                                 ", "                                 ", "C  N C                     C N  C", "C  N C                     C N  C", "C  N C                     C N  C", "C  N C                     C N  C", "                                 ", "                                 ")
            .where('M', Predicates.controller(Predicates.blocks(definition.get())))
            .where('H', Predicates.blocks("gtceu:fusion_coil"))
            .where('T', Predicates.blocks(GTBlocks.COIL_TRITANIUM.get()))
            .where('N', Predicates.blocks("gtceu:heatproof_machine_casing"))
            .where('C', Predicates.blocks("gtceu:fusion_casing_mk3"))
            .where('G', Predicates.blocks("gtceu:fusion_glass"))
            .where('S', Predicates.blocks("mekanism:supercharged_coil"))
            .where('A', Predicates.blocks('gtceu:atomic_casing').setMinGlobalLimited(35)
                .or(Predicates['autoAbilities(com.gregtechceu.gtceu.api.recipe.GTRecipeType[])'](Array.from(definition.getRecipeTypes())))
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1))
            )
            .where(' ', Predicates.any())
            .where('#', Predicates.air())
            .build())
        .workableCasingModel("gtceu:block/casings/gcym/atomic_casing",
            "gtceu:block/multiblock/fusion_reactor")
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
