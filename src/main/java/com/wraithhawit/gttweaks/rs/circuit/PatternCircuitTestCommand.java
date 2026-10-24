package com.wraithhawit.gttweaks.rs.circuit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.autocrafting.ProcessingPatternState;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridBlockEntity;
import com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternType;
import com.refinedmods.refinedstorage.common.content.DataComponents;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.FilteredContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Headless check of {@link PatternCircuit} on a live network (test rigs only; it rewrites the grid,
 * the autocrafter's slot 1 and the machine's circuit):
 * <ul>
 *   <li>{@code /gttweaks rscircuit test <grid> <autocrafter> <machine> <circuit>}: encodes
 *   "1 cobblestone + programmed circuit N -> 1 gravel" in the grid through RS's own createPattern,
 *   checks the circuit was stored and stripped, sets the machine's circuit to something else, loads
 *   the pattern into the autocrafter and asks the network for 1 gravel;</li>
 *   <li>{@code /gttweaks rscircuit check <machine> <circuit>}: a few seconds later, the machine's
 *   circuit should be N.</li>
 * </ul>
 * Pattern grid internals are package-private, hence the reflection; fine for a test command.
 */
public final class PatternCircuitTestCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("gttweaks/rs-circuit-test");
    private static final Actor ACTOR = () -> "gttweaks rscircuit test";

    private PatternCircuitTestCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gttweaks")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("rscircuit")
                        .then(Commands.literal("test")
                                .then(Commands.argument("grid", BlockPosArgument.blockPos())
                                        .then(Commands.argument("autocrafter", BlockPosArgument.blockPos())
                                                .then(Commands.argument("machine", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("circuit", IntegerArgumentType.integer(1, 32))
                                                                .executes(ctx -> test(ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "grid"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "autocrafter"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "machine"),
                                                                        IntegerArgumentType.getInteger(ctx, "circuit"))))))))
                        .then(Commands.literal("check")
                                .then(Commands.argument("machine", BlockPosArgument.blockPos())
                                        .then(Commands.argument("circuit", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> check(ctx.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "machine"),
                                                        IntegerArgumentType.getInteger(ctx, "circuit"))))))));
    }

    private static int test(CommandSourceStack source, BlockPos gridPos, BlockPos crafterPos, BlockPos machinePos,
                            int circuit) {
        ServerLevel level = source.getLevel();
        StringBuilder out = new StringBuilder();
        try {
            if (!(level.getBlockEntity(gridPos) instanceof PatternGridBlockEntity grid)) {
                return fail(source, "no pattern grid at " + gridPos.toShortString());
            }
            BlockEntity crafter = level.getBlockEntity(crafterPos);
            MetaMachine machine = MetaMachine.getMachine(level, machinePos);
            if (crafter == null || machine == null) {
                return fail(source, "need an autocrafter and a GregTech machine");
            }
            ProgrammableCircuitSlotTrait slot = machine.getTraitOptional(ProgrammableCircuitSlotTrait.class).orElse(null);
            if (slot == null) {
                return fail(source, "machine has no programmed circuit slot");
            }
            Network network = grid.getNetworkForItem();
            if (network == null || !grid.isGridActive()) {
                return fail(source, "grid is not on an active network");
            }

            // Encode through RS's own createPattern so the encode mixin runs exactly as in play.
            invoke(grid, "setPatternType", new Class<?>[]{PatternType.class}, PatternType.PROCESSING);
            ResourceContainer inputs = field(grid, "processingInput");
            ResourceContainer outputs = field(grid, "processingOutput");
            FilteredContainer patternOutput = field(grid, "patternOutput");
            inputs.clear();
            outputs.clear();
            inputs.set(0, new ResourceAmount(new ItemResource(net.minecraft.world.item.Items.COBBLESTONE), 1));
            inputs.set(1, new ResourceAmount(ItemResource.ofItemStack(PatternCircuit.circuitStack(circuit)), 1));
            outputs.set(0, new ResourceAmount(new ItemResource(net.minecraft.world.item.Items.GRAVEL), 1));
            patternOutput.setItem(0, ItemStack.EMPTY);
            grid.getPatternInput().setItem(0, new ItemStack(Items.INSTANCE.getPattern()));
            invoke(grid, "createPattern", new Class<?>[0]);
            ItemStack pattern = patternOutput.getItem(0).copy();
            Integer stored = PatternCircuit.get(pattern);
            ProcessingPatternState state = pattern.get(DataComponents.INSTANCE.getProcessingPatternState());
            boolean stripped = state != null && state.ingredients().stream()
                    .flatMap(Optional::stream)
                    .noneMatch(i -> i.input().resource() instanceof ItemResource r && PatternCircuit.isCircuit(r));
            out.append(check("encoded", !pattern.isEmpty()))
                    .append(check("circuit stored = " + circuit, stored != null && stored == circuit))
                    .append(check("circuit stripped from inputs", stripped));

            // Push: machine on another circuit, pattern into the autocrafter, ask for 1 gravel.
            int other = circuit == 1 ? 2 : 1;
            slot.setCurrentCircuit(other);
            FilteredContainer crafterPatterns = invoke(crafter, "getPatternContainer", new Class<?>[0]);
            crafterPatterns.setItem(1, pattern);
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            storage.insert(new ItemResource(net.minecraft.world.item.Items.COBBLESTONE), 4, Action.EXECUTE, ACTOR);
            Optional<?> task = network.getComponent(AutocraftingNetworkComponent.class).startTask(
                    new ItemResource(net.minecraft.world.item.Items.GRAVEL), 1, ACTOR, false, CancellationToken.NONE);
            out.append(check("machine reset to circuit " + other, slot.getCurrentCircuit() == other))
                    .append(check("craft started", task.isPresent()));
            out.append(" -- now run: gttweaks rscircuit check ").append(machinePos.getX()).append(' ')
                    .append(machinePos.getY()).append(' ').append(machinePos.getZ()).append(' ').append(circuit);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("rscircuit test aborted", e);
            return fail(source, "aborted: " + e);
        }
        LOGGER.info("rscircuit test: {}", out);
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int check(CommandSourceStack source, BlockPos machinePos, int circuit) {
        MetaMachine machine = MetaMachine.getMachine(source.getLevel(), machinePos);
        ProgrammableCircuitSlotTrait slot = machine == null ? null
                : machine.getTraitOptional(ProgrammableCircuitSlotTrait.class).orElse(null);
        String result = slot == null ? "FAIL no circuit slot at " + machinePos.toShortString()
                : check("machine circuit = " + circuit + " (is " + slot.getCurrentCircuit() + ")",
                        slot.getCurrentCircuit() == circuit);
        LOGGER.info("rscircuit check: {}", result);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static String check(String what, boolean ok) {
        return (ok ? "PASS " : "FAIL ") + what + "; ";
    }

    private static int fail(CommandSourceStack source, String why) {
        source.sendFailure(Component.literal(why));
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws ReflectiveOperationException {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object target, String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return (T) m.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }
}
