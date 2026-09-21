package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

import com.wraithhawit.gttweaks.rs.machine.RSInputBusPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSInputHatchPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSOutputBusPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSOutputHatchPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSPatternBufferPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSStockingBusPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSStockingHatchPartMachine;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * The Refined Storage multiblock parts.
 *
 * <p>Registered the way GregTech registers its own ME parts, through a {@code GTRegistrate} machine
 * builder, which is what gives them a block, an item, a block entity type and a place in the
 * multiblock structure predicates for free.
 *
 * <p>Class-loading this class <em>is</em> the registration — the definitions are static fields — so
 * it must not be touched unless Refined Storage is present, and must be touched before the registry
 * events fire. {@link #init()} exists to make that moment explicit rather than accidental.
 *
 * <p>The models point at GregTech's Applied Energistics overlay textures. That is a placeholder and
 * it will look like it: an AE2-styled part on a Refined Storage network. Own textures are a separate
 * piece of work, and a wrong-looking part that exists beats a right-looking one that does not.
 */
public final class RSMachines {

    public static final MachineDefinition RS_INPUT_BUS = GTTweaksRegistrate.REGISTRATE
            .machine("rs_input_bus", RSInputBusPartMachine::new)
            .langValue("RS Input Bus")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.IMPORT_ITEMS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
            .tooltips(
                    Component.translatable("gtceu.machine.item_bus.import.tooltip"),
                    Component.translatable("gttweaks.machine.rs.item_import.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_OUTPUT_BUS = GTTweaksRegistrate.REGISTRATE
            .machine("rs_output_bus", RSOutputBusPartMachine::new)
            .langValue("RS Output Bus")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.EXPORT_ITEMS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_output_bus"))
            .tooltips(
                    Component.translatable("gtceu.machine.item_bus.export.tooltip"),
                    Component.translatable("gttweaks.machine.rs.item_export.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_INPUT_HATCH = GTTweaksRegistrate.REGISTRATE
            .machine("rs_input_hatch", RSInputHatchPartMachine::new)
            .langValue("RS Input Hatch")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.IMPORT_FLUIDS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.fluid_hatch.import.tooltip"),
                    Component.translatable("gttweaks.machine.rs.fluid_import.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_OUTPUT_HATCH = GTTweaksRegistrate.REGISTRATE
            .machine("rs_output_hatch", RSOutputHatchPartMachine::new)
            .langValue("RS Output Hatch")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.EXPORT_FLUIDS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_output_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.fluid_hatch.export.tooltip"),
                    Component.translatable("gttweaks.machine.rs.fluid_export.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_STOCKING_BUS = GTTweaksRegistrate.REGISTRATE
            .machine("rs_stocking_bus", RSStockingBusPartMachine::new)
            .langValue("RS Stocking Bus")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.IMPORT_ITEMS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
            .tooltips(
                    Component.translatable("gtceu.machine.item_bus.import.tooltip"),
                    Component.translatable("gttweaks.machine.rs.item_stocking.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_STOCKING_HATCH = GTTweaksRegistrate.REGISTRATE
            .machine("rs_stocking_hatch", RSStockingHatchPartMachine::new)
            .langValue("RS Stocking Hatch")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.IMPORT_FLUIDS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.fluid_hatch.import.tooltip"),
                    Component.translatable("gttweaks.machine.rs.fluid_stocking.tooltip"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    public static final MachineDefinition RS_PATTERN_BUFFER = GTTweaksRegistrate.REGISTRATE
            .machine("rs_pattern_buffer", RSPatternBufferPartMachine::new)
            .langValue("RS Pattern Buffer")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
            .tooltips(
                    Component.translatable("gttweaks.machine.rs.pattern_buffer.tooltip.0"),
                    Component.translatable("gttweaks.machine.rs.pattern_buffer.tooltip.1"),
                    Component.translatable("gtceu.part_sharing.enabled"))
            .register();

    private static boolean built;

    private RSMachines() {
    }

    /**
     * Builds the definitions above, the first time a registry is dispatched.
     *
     * <p>The timing is not a preference. Building a machine class-loads GregTech's
     * {@code GTMachineModels}, whose static initialiser reads GregTech's casings, and those are put
     * in place by {@code GTBlocks.init()} — which GregTech itself runs from inside its
     * {@code RegisterEvent} handler, immediately before class-loading its own machines. Anything
     * earlier than that dies with
     * {@code NullPointerException: null key in entry: null=gtceu:block/casings/solid/machine_casing_bronze_plated_bricks},
     * and "anything earlier" includes both the mod constructor and {@code gtInitComplete()}.
     *
     * <p>So this runs on the same first dispatch GregTech uses, just afterwards, which the listener
     * order in {@code GTTweaks} arranges: GregTech's own handler and registrate were registered
     * while GregTech was constructing, and this mod constructs later.
     */
    public static void initOnce() {
        if (built) {
            return;
        }
        built = true;
        // Touching the class is the registration; naming a field here is what forces it.
        Objects.requireNonNull(RS_INPUT_BUS);
        Objects.requireNonNull(RS_OUTPUT_BUS);
        Objects.requireNonNull(RS_INPUT_HATCH);
        Objects.requireNonNull(RS_OUTPUT_HATCH);
        Objects.requireNonNull(RS_STOCKING_BUS);
        Objects.requireNonNull(RS_STOCKING_HATCH);
    }
}
