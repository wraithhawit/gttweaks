package com.wraithhawit.gttweaks.rs;

import java.util.List;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.machine.RSInputBusPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSPatternBufferPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSPatternBufferProxyPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSInputHatchPartMachine;
import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferSlot;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredFluidSlot;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;

/**
 * Reads and configures a Refined Storage part from the console, so the parts can be tested without a
 * client.
 *
 * <p>Both halves earn their place. {@code info} is the only way to see, headlessly, whether a part
 * actually joined a network — the answer otherwise lives in a GUI line — and it reports what the node
 * believes rather than what the machine last cached, because the interesting failures are exactly the
 * ones where those two differ. {@code config} stands in for the slot UI that does not exist yet: with
 * it, a dedicated server can be told what a part should stock and then watched doing it.
 *
 * <p>{@code extract} drives the one path a multiblock would drive, and exists because the
 * {@code simulate} flag cannot be reached any other way. GregTech runs a simulated recipe pass
 * before a real one, and a stocking part that ignored the flag would consume the inputs of every
 * recipe it merely considered — a bug that is invisible in game until the network drains. Nothing a
 * player or a hopper can do asks for a simulated extraction, so the flag is exposed here instead.
 *
 * <p>Items and fluids are separate subcommands rather than one that guesses, because there is no
 * vanilla fluid argument to pair with {@link ItemArgument} — a fluid is named by id and looked up in
 * the registry — and because a part accepts exactly one of the two.
 *
 * <p>Not registered through {@code @EventBusSubscriber}: that scans and loads the class on every
 * launch, and this one names Refined Storage types in its signatures. It is wired up from
 * {@code GTTweaks} instead, on the branch that already knows Refined Storage is present.
 */
public final class RSPartCommand {

    private RSPartCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        var itemAmount = Commands.argument("amount", LongArgumentType.longArg(1))
                .executes(context -> configItem(context,
                        IntegerArgumentType.getInteger(context, "slot"),
                        LongArgumentType.getLong(context, "amount")));
        var item = Commands.literal("item")
                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                        .then(itemAmount));
        var fluidAmount = Commands.argument("amount", LongArgumentType.longArg(1))
                .executes(context -> configFluid(context,
                        IntegerArgumentType.getInteger(context, "slot"),
                        LongArgumentType.getLong(context, "amount")));
        var fluid = Commands.literal("fluid")
                .then(Commands.argument("fluid", ResourceLocationArgument.id())
                        .then(fluidAmount));
        var clear = Commands.literal("clear")
                .executes(context -> clear(context, IntegerArgumentType.getInteger(context, "slot")));
        var slot = Commands.argument("slot", IntegerArgumentType.integer(0))
                .then(clear)
                .then(item)
                .then(fluid);
        var extract = Commands.literal("extract")
                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .then(Commands.argument("simulate", BoolArgumentType.bool())
                                        .executes(context -> extract(context,
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                BoolArgumentType.getBool(context, "simulate"))))));
        var buffer = Commands.literal("buffer")
                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> bufferPush(context,
                                                        IntegerArgumentType.getInteger(context, "slot"),
                                                        new ResourceAmount(ItemResource.ofItemStack(
                                                                ItemArgument.getItem(context, "item")
                                                                        .createItemStack(1, false)),
                                                                LongArgumentType.getLong(context, "amount")))))))
                        .then(Commands.literal("fluid")
                                .then(Commands.argument("fluid", ResourceLocationArgument.id())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(context -> bufferPushFluid(context,
                                                        IntegerArgumentType.getInteger(context, "slot"),
                                                        ResourceLocationArgument.getId(context, "fluid"),
                                                        LongArgumentType.getLong(context, "amount")))))));
        var pos = Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(RSPartCommand::info)
                .then(Commands.literal("config").then(slot))
                .then(extract)
                .then(buffer)
                .then(Commands.literal("link")
                        .then(Commands.argument("buffer", BlockPosArgument.blockPos())
                                .executes(context -> link(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "buffer")))));
        event.getDispatcher().register(Commands.literal("gttweaks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rspart").then(pos)));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (machine instanceof RSPatternBufferProxyPartMachine proxy) {
            say(source, "part: " + machine.getDefinition().getId());
            say(source, "buffer link: " + (proxy.getBufferPos() == null ? "none"
                    : proxy.getBufferPos().toShortString()
                            + (proxy.getBuffer() == null ? " (not a buffer)" : " (resolved)")));
            return 1;
        }
        RSNetworkPart part = (RSNetworkPart) machine;
        Network network = part.getNodeHolder().getNode().getNetwork();
        StorageNetworkComponent storage = part.getNodeHolder().storage();
        say(source, "part: " + machine.getDefinition().getId());
        say(source, "machine says online: " + part.isOnline());
        say(source, "node network: " + (network == null ? "none" : "joined")
                + ", active: " + part.getNodeHolder().getNode().isActive());
        say(source, "network storage: " + (storage == null ? "unreachable"
                : storage.getAll().size() + " resource types"));
        if (machine instanceof RSPatternBufferPartMachine buffer) {
            say(source, "proxies linked: " + buffer.getProxies().size());
            say(source, "patterns held: " + patternCount(buffer));
            List<RSBufferSlot> bufferSlots = buffer.getSlots();
            for (int i = 0; i < bufferSlots.size(); i++) {
                RSBufferSlot bufferSlot = bufferSlots.get(i);
                say(source, "  buffer slot " + i + (bufferSlot.isEmpty() ? ": empty"
                        : ": " + bufferSlot.getItems() + " " + bufferSlot.getFluids()));
            }
            return 1;
        }
        RSConfiguredSlot[] slots = configuredSlots(machine);
        if (slots == null) {
            say(source, "no configured slots on this part");
            return 1;
        }
        for (int i = 0; i < slots.length; i++) {
            ResourceAmount config = slots[i].getConfig();
            ResourceAmount stock = slots[i].getStock();
            if (config == null && stock == null) {
                continue;
            }
            say(source, "  slot " + i + ": config " + describe(config) + ", stock " + describe(stock));
        }
        return 1;
    }

    private static int configItem(CommandContext<CommandSourceStack> context, int slot, long amount)
            throws CommandSyntaxException {
        RSConfiguredSlot target = slot(context, slot);
        if (target == null) {
            return 0;
        }
        ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
        // Compared against what was asked for, not against null: a slot that refuses the resource
        // keeps whatever it already had, so a null check would call a refusal a success whenever the
        // slot was already configured.
        ResourceAmount intended = new ResourceAmount(ItemResource.ofItemStack(stack), amount);
        target.setConfig(intended);
        if (!intended.equals(target.getConfig())) {
            say(context.getSource(), "that part does not take items");
            return 0;
        }
        say(context.getSource(), "slot " + slot + " set to " + amount + " x "
                + stack.getHoverName().getString());
        return 1;
    }

    private static int configFluid(CommandContext<CommandSourceStack> context, int slot, long amount)
            throws CommandSyntaxException {
        RSConfiguredSlot target = slot(context, slot);
        if (target == null) {
            return 0;
        }
        ResourceLocation id = ResourceLocationArgument.getId(context, "fluid");
        Fluid found = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (found == null) {
            say(context.getSource(), "unknown fluid '" + id + "'");
            return 0;
        }
        ResourceAmount intended = new ResourceAmount(new FluidResource(found), amount);
        target.setConfig(intended);
        if (!intended.equals(target.getConfig())) {
            say(context.getSource(), "that part does not take fluids");
            return 0;
        }
        say(context.getSource(), "slot " + slot + " set to " + amount + " x " + id);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context, int slot)
            throws CommandSyntaxException {
        RSConfiguredSlot target = slot(context, slot);
        if (target == null) {
            return 0;
        }
        target.setConfig(null);
        say(context.getSource(), "slot " + slot + " cleared");
        return 1;
    }

    /**
     * Takes from a slot exactly as a recipe would, so the {@code simulate} flag can be checked.
     *
     * <p>Goes through the slot's own {@code extractItem}/{@code drain} rather than the part, because
     * that is the method GregTech's recipe logic calls and the one a stocking slot overrides to reach
     * the network.
     */
    private static int extract(CommandContext<CommandSourceStack> context, int slot, int amount,
            boolean simulate) throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        String how = simulate ? "simulated" : "executed";
        if (machine instanceof RSInputBusPartMachine bus) {
            RSConfiguredItemSlot[] slots = bus.getItemHandler().getInventory();
            if (slot >= slots.length) {
                say(source, "slot out of range, this part has " + slots.length);
                return 0;
            }
            ItemStack got = slots[slot].extractItem(0, amount, simulate);
            say(source, how + " extract of " + amount + " from slot " + slot + " gave "
                    + (got.isEmpty() ? "nothing"
                            : got.getCount() + " x " + got.getHoverName().getString()));
            return 1;
        }
        if (machine instanceof RSInputHatchPartMachine hatch) {
            RSConfiguredFluidSlot[] slots = hatch.getFluidHandler().getInventory();
            if (slot >= slots.length) {
                say(source, "slot out of range, this part has " + slots.length);
                return 0;
            }
            FluidStack got = slots[slot].drain(amount, simulate
                    ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            say(source, how + " drain of " + amount + " from slot " + slot + " gave "
                    + (got.isEmpty() ? "nothing"
                            : got.getAmount() + " x " + BuiltInRegistries.FLUID.getKey(got.getFluid())));
            return 1;
        }
        say(source, "that part has no configured slots");
        return 0;
    }

    private static int patternCount(RSPatternBufferPartMachine buffer) {
        int held = 0;
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++) {
            if (!buffer.getPatternInventory().getStackInSlot(i).isEmpty()) {
                held++;
            }
        }
        return held;
    }

    /**
     * Puts a resource straight into a pattern buffer slot, as a pattern's delivery would.
     *
     * <p>Stands in for a Refined Storage autocrafting task, which cannot be raised without an
     * encoded pattern item and a player to encode it. This reaches the same {@code RSBufferSlot} the
     * sink fills, so what it exercises is the holding, the refund and the persistence — not the
     * pattern publishing or the task stepping, which remain unverified.
     */
    private static int bufferPush(CommandContext<CommandSourceStack> context, int slot,
            ResourceAmount amount) throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (!(machine instanceof RSPatternBufferPartMachine buffer)) {
            say(source, "that part is not a pattern buffer");
            return 0;
        }
        List<RSBufferSlot> slots = buffer.getSlots();
        if (slot >= slots.size()) {
            say(source, "slot out of range, this part has " + slots.size());
            return 0;
        }
        boolean held = slots.get(slot).add(amount);
        say(source, held ? "pushed " + amount.amount() + " into buffer slot " + slot
                : "buffer slot " + slot + " refused that resource");
        return held ? 1 : 0;
    }

    /**
     * Points a proxy at a buffer, standing in for the data stick.
     *
     * <p>GregTech links the two by right-clicking each with a data stick, which needs a player. This
     * reaches the same {@code setBuffer}, so it links exactly as the stick does.
     */
    private static int link(CommandContext<CommandSourceStack> context, BlockPos target)
            throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (!(machine instanceof RSPatternBufferProxyPartMachine proxy)) {
            say(source, "that part is not a pattern buffer proxy");
            return 0;
        }
        proxy.setBuffer(target);
        say(source, proxy.getBuffer() == null
                ? "nothing linked: " + target.toShortString() + " is not a pattern buffer"
                : "linked to the buffer at " + target.toShortString());
        return proxy.getBuffer() == null ? 0 : 1;
    }

    private static int bufferPushFluid(CommandContext<CommandSourceStack> context, int slot,
            ResourceLocation id, long amount) throws CommandSyntaxException {
        Fluid found = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (found == null) {
            say(context.getSource(), "unknown fluid '" + id + "'");
            return 0;
        }
        return bufferPush(context, slot, new ResourceAmount(new FluidResource(found), amount));
    }

    @Nullable
    private static RSConfiguredSlot slot(CommandContext<CommandSourceStack> context, int slot)
            throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return null;
        }
        RSConfiguredSlot[] slots = configuredSlots(machine);
        if (slots == null) {
            say(context.getSource(), "that part has no configured slots");
            return null;
        }
        if (slot >= slots.length) {
            say(context.getSource(), "slot out of range, this part has " + slots.length);
            return null;
        }
        return slots[slot];
    }

    /**
     * The configured slots of whichever part this is, or null for one that has none — the output
     * parts, which take no configuration at all.
     */
    @Nullable
    private static RSConfiguredSlot[] configuredSlots(MetaMachine machine) {
        if (machine instanceof RSInputBusPartMachine bus) {
            RSConfiguredItemSlot[] slots = bus.getItemHandler().getInventory();
            RSConfiguredSlot[] out = new RSConfiguredSlot[slots.length];
            System.arraycopy(slots, 0, out, 0, slots.length);
            return out;
        }
        if (machine instanceof RSInputHatchPartMachine hatch) {
            RSConfiguredFluidSlot[] slots = hatch.getFluidHandler().getInventory();
            RSConfiguredSlot[] out = new RSConfiguredSlot[slots.length];
            for (int i = 0; i < slots.length; i++) {
                out[i] = slots[i].slot();
            }
            return out;
        }
        return null;
    }

    @Nullable
    private static MetaMachine part(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        // The proxy is deliberately not an RSNetworkPart -- it holds no node and is not on the
        // network -- but it is still one of these parts and still has to be inspectable here.
        if (!(machine instanceof RSNetworkPart) && !(machine instanceof RSPatternBufferProxyPartMachine)) {
            say(context.getSource(), "no Refined Storage part at " + pos.toShortString());
            return null;
        }
        return machine;
    }

    private static String describe(@Nullable ResourceAmount amount) {
        if (amount == null) {
            return "-";
        }
        return amount.amount() + " x " + amount.resource();
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
