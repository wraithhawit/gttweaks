package com.wraithhawit.gttweaks;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /gttweaks recipematch <crafting|smelting|blasting> <input>}: lists every loaded recipe that
 * accepts an input, with the item each one gives.
 *
 * <p>For checking duplicate recipes without a player. The recipe probe only says whether an id
 * loaded; this asks the recipe manager what a real grid matches, after KubeJS, AlmostUnified and
 * conditions have all had their say, so two recipes fighting over one grid show up as two lines.
 *
 * <p>Crafting input is rows separated by {@code /} and cells by {@code ,}; {@code -} is an empty
 * cell and {@code id*n} repeats a cell n times, so a full grid of nether stars is
 * {@code minecraft:nether_star*3/minecraft:nether_star*3/minecraft:nether_star*3}. Short rows are
 * padded with empty cells. Smelting and blasting take a single item id. Output goes to the command
 * source (RCON included) and the log.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeMatchCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gttweaks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("recipematch")
                        .then(Commands.literal("crafting")
                                .then(Commands.argument("grid", StringArgumentType.greedyString())
                                        .executes(context -> crafting(context,
                                                StringArgumentType.getString(context, "grid")))))
                        .then(Commands.literal("smelting")
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> cooking(context, RecipeType.SMELTING,
                                                StringArgumentType.getString(context, "item")))))
                        .then(Commands.literal("blasting")
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> cooking(context, RecipeType.BLASTING,
                                                StringArgumentType.getString(context, "item")))))));
    }

    private static int crafting(CommandContext<CommandSourceStack> context, String grid) {
        final CommandSourceStack source = context.getSource();
        final List<List<ItemStack>> rows = new ArrayList<>();
        int width = 0;
        for (String row : grid.trim().split("/")) {
            final List<ItemStack> cells = new ArrayList<>();
            for (String cell : row.split(",")) {
                String id = cell.trim();
                int repeat = 1;
                final int star = id.lastIndexOf('*');
                if (star > 0) {
                    try {
                        repeat = Integer.parseInt(id.substring(star + 1));
                    } catch (NumberFormatException e) {
                        return fail(source, "bad repeat count in '" + cell.trim() + "'");
                    }
                    id = id.substring(0, star);
                }
                final ItemStack stack;
                if (id.equals("-") || id.isEmpty()) {
                    stack = ItemStack.EMPTY;
                } else {
                    final Optional<Item> item = item(id);
                    if (item.isEmpty()) {
                        return fail(source, "unknown item '" + id + "'");
                    }
                    stack = new ItemStack(item.get());
                }
                for (int i = 0; i < repeat; i++) {
                    cells.add(stack.copy());
                }
            }
            width = Math.max(width, cells.size());
            rows.add(cells);
        }
        if (width > 3 || rows.size() > 3) {
            return fail(source, "grid is " + width + "x" + rows.size() + ", larger than 3x3");
        }
        final List<ItemStack> items = new ArrayList<>();
        for (List<ItemStack> row : rows) {
            items.addAll(row);
            for (int i = row.size(); i < width; i++) {
                items.add(ItemStack.EMPTY);
            }
        }
        final CraftingInput input = CraftingInput.of(width, rows.size(), items);
        final MinecraftServer server = source.getServer();
        final ServerLevel level = server.overworld();
        final List<String> lines = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value().matches(input, level)) {
                lines.add(holder.id() + " -> " + describe(holder.value().assemble(input, server.registryAccess())));
            }
        }
        return report(source, "crafting " + grid.trim(), lines);
    }

    private static <T extends AbstractCookingRecipe> int cooking(CommandContext<CommandSourceStack> context,
            RecipeType<T> type, String id) {
        final CommandSourceStack source = context.getSource();
        final Optional<Item> item = item(id.trim());
        if (item.isEmpty()) {
            return fail(source, "unknown item '" + id.trim() + "'");
        }
        final SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item.get()));
        final MinecraftServer server = source.getServer();
        final ServerLevel level = server.overworld();
        final List<String> lines = new ArrayList<>();
        for (RecipeHolder<T> holder : server.getRecipeManager().getAllRecipesFor(type)) {
            if (holder.value().matches(input, level)) {
                lines.add(holder.id() + " -> " + describe(holder.value().assemble(input, server.registryAccess())));
            }
        }
        return report(source, BuiltInRegistries.RECIPE_TYPE.getKey(type) + " " + id.trim(), lines);
    }

    private static Optional<Item> item(String id) {
        final ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "(empty)" : stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static int report(CommandSourceStack source, String what, List<String> lines) {
        final String header = "[gttweaks] recipematch " + what + ": " + lines.size() + " match(es)";
        LOGGER.info(header);
        source.sendSuccess(() -> Component.literal(header), false);
        for (String line : lines) {
            LOGGER.info("[gttweaks]   {}", line);
            source.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        }
        return lines.size();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[gttweaks] recipematch: " + message));
        return 0;
    }
}
