package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import com.wraithhawit.gttweaks.rs.machine.RSBusPartMachine;
import com.wraithhawit.gttweaks.rs.machine.RSInputBusPartMachine;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredItemSlot;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
 * it, a dedicated server can be told what a bus should stock and then watched doing it.
 *
 * <p>Not registered through {@code @EventBusSubscriber}: that scans and loads the class on every
 * launch, and this one names Refined Storage types in its signatures. It is wired up from
 * {@code GTTweaks} instead, on the branch that already knows Refined Storage is present.
 */
public final class RSPartCommand {

    private RSPartCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        var amount = Commands.argument("amount", LongArgumentType.longArg(1))
                .executes(context -> config(context,
                        IntegerArgumentType.getInteger(context, "slot"),
                        LongArgumentType.getLong(context, "amount")));
        var item = Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                .then(amount);
        var clear = Commands.literal("clear")
                .executes(context -> clear(context, IntegerArgumentType.getInteger(context, "slot")));
        var slot = Commands.argument("slot", IntegerArgumentType.integer(0))
                .then(clear)
                .then(item);
        var pos = Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(RSPartCommand::info)
                .then(Commands.literal("config").then(slot));
        event.getDispatcher().register(Commands.literal("gttweaks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rspart").then(pos)));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RSBusPartMachine part = part(context);
        if (part == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        Network network = part.getNodeHolder().getNode().getNetwork();
        StorageNetworkComponent storage = part.getNodeHolder().storage();
        say(source, "part: " + part.getDefinition().getId());
        say(source, "machine says online: " + part.isOnline());
        say(source, "node network: " + (network == null ? "none" : "joined")
                + ", active: " + part.getNodeHolder().getNode().isActive());
        say(source, "network storage: " + (storage == null ? "unreachable"
                : storage.getAll().size() + " resource types"));
        if (part instanceof RSInputBusPartMachine bus) {
            RSConfiguredItemSlot[] slots = bus.getItemHandler().getInventory();
            for (int i = 0; i < slots.length; i++) {
                ResourceAmount config = slots[i].getConfig();
                ResourceAmount stock = slots[i].getStock();
                if (config == null && stock == null) {
                    continue;
                }
                say(source, "  slot " + i + ": config " + describe(config) + ", stock " + describe(stock));
            }
        }
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> context, int slot, long amount)
            throws CommandSyntaxException {
        RSConfiguredItemSlot target = slot(context, slot);
        if (target == null) {
            return 0;
        }
        ItemStack stack = ItemArgument.getItem(context, "item").createItemStack(1, false);
        target.setConfig(new ResourceAmount(ItemResource.ofItemStack(stack), amount));
        say(context.getSource(), "slot " + slot + " set to " + amount + " x " + stack.getHoverName().getString());
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context, int slot)
            throws CommandSyntaxException {
        RSConfiguredItemSlot target = slot(context, slot);
        if (target == null) {
            return 0;
        }
        target.setConfig(null);
        say(context.getSource(), "slot " + slot + " cleared");
        return 1;
    }

    @Nullable
    private static RSConfiguredItemSlot slot(CommandContext<CommandSourceStack> context, int slot)
            throws CommandSyntaxException {
        RSBusPartMachine part = part(context);
        if (!(part instanceof RSInputBusPartMachine bus)) {
            if (part != null) {
                say(context.getSource(), "that part has no configured item slots");
            }
            return null;
        }
        RSConfiguredItemSlot[] slots = bus.getItemHandler().getInventory();
        if (slot >= slots.length) {
            say(context.getSource(), "slot out of range, this part has " + slots.length);
            return null;
        }
        return slots[slot];
    }

    @Nullable
    private static RSBusPartMachine part(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (!(machine instanceof RSBusPartMachine part)) {
            say(context.getSource(), "no Refined Storage part at " + pos.toShortString());
            return null;
        }
        return part;
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
