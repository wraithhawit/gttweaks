// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.

GTCEuStartupEvents.registry('gtceu:recipe_type', allthemods => {
    allthemods.create('star_forge')
        .category('gregstar')
        .setEUIO('in')
        .setMaxIOSize(15, 5, 5, 0)
        .setItemSlotOverlay(IO.IN, 0, GTGuiTextures.SOLIDIFIER_OVERLAY)
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
        .setSound(GTSoundEntries.ARC);
})

GTCEuStartupEvents.registry('gtceu:machine', allthemods => {
    allthemods.create('star_forge', 'multiblock')
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeTypes('star_forge')
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH, GTRecipeModifiers.OC_NON_PERFECT])
        .appearanceBlock(GCYMBlocks.CASING_ATOMIC)
        .pattern(definition => FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("               ", "               ", "               ", "               ", "               ", "      W W      ", "     CWEWC     ", "      W W      ", "               ", "               ", "               ", "               ", "               ")
            .slice("               ", "               ", "               ", "               ", "      GCG      ", "     CWCWC     ", "   CCPPPPPCC   ", "     CWCWC     ", "      GCG      ", "               ", "               ", "               ", "               ")
            .slice("               ", "               ", "      GCG      ", "     GGCGG     ", "    GG   GG    ", "   CC WCW CC   ", "  CPPCWCWCPPC  ", "   CC WCW CC   ", "    GG   GG    ", "     GGCGG     ", "      GCG      ", "               ", "               ")
            .slice("               ", "      GCG      ", "    GG   GG    ", "   GG     GG   ", "   G       G   ", "  C         C  ", " CPCC  S  CCPC ", "  C         C  ", "   G       G   ", "   GG     GG   ", "    GG   GG    ", "      GCG      ", "               ")
            .slice("               ", "     GGCGG     ", "   GG     GG   ", "   G       G   ", "  G         G  ", "  C         C  ", " CPC       CPC ", "  C         C  ", "  G         G  ", "   G       G   ", "   GG     GG   ", "     GGCGG     ", "               ")
            .slice("      CCC      ", "    GG C GG    ", "   G       G   ", "  G         G  ", "  G         G  ", " C           C ", "CPC         CPC", " C           C ", "  G         G  ", "  G         G  ", "   G       G   ", "    GG C GG    ", "      CCC      ")
            .slice("     CFCFC     ", "   GG CCC GG   ", "  G    C    G  ", "  G         G  ", " G           G ", "WWW         WWW", "WPW         WPW", "WWW         WWW", " G           G ", "  G         G  ", "  G    C    G  ", "   GG CCC GG   ", "     CFCFC     ")
            .slice("     CCFCC     ", "   CCCCPCCCC   ", "  C   CCC   C  ", "  C    S    C  ", " C           C ", " CC         CC ", "EPCS   I   SCPE", " CC         CC ", " C           C ", "  C    S    C  ", "  C   CCC   C  ", "   CCCCPCCCC   ", "     CCFCC     ")
            .slice("     CFCFC     ", "   GG CCC GG   ", "  G    C    G  ", "  G         G  ", " G           G ", "WWW         WWW", "WPW         WPW", "WWW         WWW", " G           G ", "  G         G  ", "  G    C    G  ", "   GG CCC GG   ", "     CFCFC     ")
            .slice("      CCC      ", "    GG C GG    ", "   G       G   ", "  G         G  ", "  G         G  ", " C           C ", "CPC         CPC", " C           C ", "  G         G  ", "  G         G  ", "   G       G   ", "    GG C GG    ", "      CCC      ")
            .slice("               ", "     GGCGG     ", "   GG     GG   ", "   G       G   ", "  G         G  ", "  C         C  ", " CPC       CPC ", "  C         C  ", "  G         G  ", "   G       G   ", "   GG     GG   ", "     GGCGG     ", "               ")
            .slice("               ", "      GCG      ", "    GG   GG    ", "   GG     GG   ", "   G       G   ", "  C         C  ", " CPCC  S  CCPC ", "  C         C  ", "   G       G   ", "   GG     GG   ", "    GG   GG    ", "      GCG      ", "               ")
            .slice("               ", "               ", "      GCG      ", "     GGCGG     ", "    GG   GG    ", "   CC WCW CC   ", "  CPPCWCWCPPC  ", "   CC WCW CC   ", "    GG   GG    ", "     GGCGG     ", "      GCG      ", "               ", "               ")
            .slice("               ", "               ", "               ", "               ", "      GCG      ", "     CWCWC     ", "   CCPPPPPCC   ", "     CWCWC     ", "      GCG      ", "               ", "               ", "               ", "               ")
            .slice("               ", "               ", "               ", "               ", "               ", "      W W      ", "     AWMWA     ", "      W W      ", "               ", "               ", "               ", "               ", "               ")
            .where('M', Predicates.controller(Predicates.blocks(definition.get())))
            .where('W', Predicates.blocks(GTBlocks.COIL_TRINIUM.get()))
            .where("P", Predicates.blocks(GTBlocks.SUPERCONDUCTING_COIL.get()))
            .where('G', Predicates.blocks("connectedglass:clear_glass_black"))
            .where('I', Predicates.blocks("allthetweaks:atm_star_block"))
            .where('S', Predicates.blocks("mekanism:supercharged_coil"))
            .where('E', Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.INPUT_ENERGY).setMinGlobalLimited(2)
                .or(Predicates.blocks('gtceu:atomic_casing'))) 
            .where('C', Predicates.blocks('gtceu:atomic_casing')
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.PARALLEL_HATCH).setExactLimit(1))) 
            .where('F', Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(5)
                .or(Predicates.blocks('gtceu:atomic_casing'))) 
            .where('A', Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.IMPORT_ITEMS).setExactLimit(1)
                .or(Predicates['abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility)'](PartAbility.EXPORT_ITEMS)).setExactLimit(1)
                .or(Predicates.blocks('gtceu:atomic_casing'))) 
            .where(' ', Predicates.any())
            .build())
        .workableCasingModel("gtceu:block/casings/gcym/atomic_casing", /*"gtceu:block/casings/gcym/atomic_casing"*/
            "gtceu:block/multiblock/implosion_compressor")
})

// This File has been authored by AllTheMods Staff, or a Community contributor for use in AllTheMods - AllTheMods 9.
// As all AllTheMods packs are licensed under All Rights Reserved, this file is not allowed to be used in any public packs not released by the AllTheMods Team, without explicit permission.
