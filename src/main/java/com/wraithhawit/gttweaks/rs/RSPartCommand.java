package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

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
import com.wraithhawit.gttweaks.rs.machine.RSInputHatchPartMachine;
import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;
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
        var pos = Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(RSPartCommand::info)
                .then(Commands.literal("config").then(slot));
        event.getDispatcher().register(Commands.literal("gttweaks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rspart").then(pos)));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MetaMachine machine = part(context);
        if (machine == null) {
            return 0;
        }
        RSNetworkPart part = (RSNetworkPart) machine;
        CommandSourceStack source = context.getSource();
        Network network = part.getNodeHolder().getNode().getNetwork();
        StorageNetworkComponent storage = part.getNodeHolder().storage();
        say(source, "part: " + machine.getDefinition().getId());
        say(source, "machine says online: " + part.isOnline());
        say(source, "node network: " + (network == null ? "none" : "joined")
                + ", active: " + part.getNodeHolder().getNode().isActive());
        say(source, "network storage: " + (storage == null ? "unreachable"
                : storage.getAll().size() + " resource types"));
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
        if (!(machine instanceof RSNetworkPart)) {
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
