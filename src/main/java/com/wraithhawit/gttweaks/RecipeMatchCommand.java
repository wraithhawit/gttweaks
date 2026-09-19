package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /gttweaks recipematch}: asks the loaded recipe manager which recipes accept or produce an
 * item, for checking duplicate recipes without a player.
 *
 * <ul>
 * <li>{@code crafting <grid>} — every crafting recipe a grid matches, with its result. Rows are
 * separated by {@code /} and cells by {@code ,}; {@code -} is an empty cell and {@code id*n} repeats
 * a cell n times, so a full grid of nether stars is
 * {@code minecraft:nether_star*3/minecraft:nether_star*3/minecraft:nether_star*3}. Short rows are
 * padded with empty cells.</li>
 * <li>{@code smelting <item>} / {@code blasting <item>} — every furnace recipe that takes the item.</li>
 * <li>{@code output <item>} / {@code input <item>} — every recipe of any type, GregTech machines
 * included, that produces or consumes the item.</li>
 * </ul>
 *
 * <p>The recipe probe only says whether an id loaded; this runs after KubeJS, AlmostUnified and
 * conditions have all had their say, so two recipes fighting over one input show up as two lines.
 *
 * <p>For {@code output} and {@code input}, a GregTech recipe is read from its item contents (the
 * items an output hands out, or whether an input ingredient accepts the item), and any other recipe
 * from {@code getResultItem} and {@code getIngredients}. Recipes whose result depends on the input
 * (a few special crafting recipes) report an empty result and are missed. At most {@link #MAX_LINES}
 * lines are sent; the count covers them all. Output goes to the command source (RCON included) and
 * the log.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeMatchCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LINES = 60;

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
                                                StringArgumentType.getString(context, "item")))))
                        .then(Commands.literal("output")
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> uses(context, true,
                                                StringArgumentType.getString(context, "item")))))
                        .then(Commands.literal("input")
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> uses(context, false,
                                                StringArgumentType.getString(context, "item")))))
                        .then(Commands.literal("tags")
                                .then(Commands.literal("item")
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .executes(context -> tags(context, false,
                                                        StringArgumentType.getString(context, "id")))))
                                .then(Commands.literal("fluid")
                                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                                .executes(context -> tags(context, true,
                                                        StringArgumentType.getString(context, "id"))))))));
    }

    /** {@code tags <item|fluid> <id>}: the tags an item or fluid is in right now, runtime tags included. */
    private static int tags(CommandContext<CommandSourceStack> context, boolean fluid, String id) {
        final CommandSourceStack source = context.getSource();
        final ResourceLocation location = ResourceLocation.tryParse(id.trim());
        final List<String> lines = new ArrayList<>();
        if (fluid) {
            final var entry = location == null ? Optional.<net.minecraft.world.level.material.Fluid>empty()
                    : BuiltInRegistries.FLUID.getOptional(location);
            if (entry.isEmpty()) {
                return fail(source, "unknown fluid '" + id.trim() + "'");
            }
            entry.get().builtInRegistryHolder().tags().forEach(tag -> lines.add("#" + tag.location()));
        } else {
            final Optional<Item> entry = location == null ? Optional.empty() : item(id.trim());
            if (entry.isEmpty()) {
                return fail(source, "unknown item '" + id.trim() + "'");
            }
            entry.get().builtInRegistryHolder().tags().forEach(tag -> lines.add("#" + tag.location()));
        }
        lines.sort(String::compareTo);
        return report(source, "tags " + (fluid ? "fluid " : "item ") + id.trim(), lines);
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

    /** Every recipe, of any type, that produces ({@code output}) or consumes the item. */
    private static int uses(CommandContext<CommandSourceStack> context, boolean output, String id) {
        final CommandSourceStack source = context.getSource();
        final Optional<Item> item = item(id.trim());
        if (item.isEmpty()) {
            return fail(source, "unknown item '" + id.trim() + "'");
        }
        final ItemStack stack = new ItemStack(item.get());
        final MinecraftServer server = source.getServer();
        final List<String> lines = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            final Recipe<?> recipe = holder.value();
            final String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            if (recipe instanceof GTRecipe gt) {
                final List<Content> contents = output
                        ? gt.getOutputContents(GTRecipeCapabilities.ITEM)
                        : gt.getInputContents(GTRecipeCapabilities.ITEM);
                for (Content content : contents) {
                    if (!(content.content() instanceof SizedIngredient sized)) {
                        continue;
                    }
                    if (output ? producesItem(sized, item.get()) : sized.ingredient().test(stack)) {
                        lines.add(type + " " + holder.id() + (output ? " -> " : " <- ") + sized.count()
                                + "x" + cost(gt));
                        break;
                    }
                }
            } else if (output) {
                final ItemStack result = recipe.getResultItem(server.registryAccess());
                if (!result.isEmpty() && result.is(item.get())) {
                    lines.add(type + " " + holder.id() + " -> " + result.getCount() + "x");
                }
            } else {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (!ingredient.isEmpty() && ingredient.test(stack)) {
                        lines.add(type + " " + holder.id());
                        break;
                    }
                }
            }
        }
        return report(source, (output ? "output " : "input ") + id.trim(), lines);
    }

    /**
     * The recipe's running cost, as {@code [EU/t, tier, duration, coil temperature]}.
     *
     * <p>GregTech stores the voltage as an {@link EnergyStack} rather than a field, and as a
     * <em>tick</em> input, since it is drawn every tick rather than once — {@code getInputContents}
     * alone reports nothing for an electric blast furnace recipe. The required coil temperature
     * lives in {@link GTRecipe#data} under {@code ebf_temp}. All of it is absent on most recipes,
     * so every part is optional and the whole thing collapses to an empty string when there is
     * none.
     */
    private static String cost(GTRecipe recipe) {
        final StringBuilder parts = new StringBuilder();
        appendEnergy(parts, recipe.getTickInputContents(GTRecipeCapabilities.EU), " EU/t ");
        if (parts.length() == 0) {
            appendEnergy(parts, recipe.getInputContents(GTRecipeCapabilities.EU), " EU ");
        }
        if (recipe.duration > 0) {
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(recipe.duration).append(" ticks");
        }
        if (recipe.data != null && recipe.data.contains("ebf_temp", Tag.TAG_ANY_NUMERIC)) {
            if (parts.length() > 0) {
                parts.append(", ");
            }
            parts.append(recipe.data.getInt("ebf_temp")).append(" K");
        }
        return parts.length() == 0 ? "" : "  [" + parts + "]";
    }

    /**
     * Appends the first non-empty voltage in {@code contents}, with its tier name.
     *
     * <p>{@code unit} distinguishes the two places GregTech keeps energy: a tick input is drawn
     * every tick and is EU/t, a plain input is drawn once. Reporting the second as EU/t would
     * overstate it by the recipe's whole duration. Only the first entry is read; a recipe with
     * several is not something this reports faithfully, and none is known to exist.
     */
    private static void appendEnergy(StringBuilder parts, List<Content> contents, String unit) {
        for (Content content : contents) {
            if (content.content() instanceof EnergyStack energy && !energy.isEmpty()) {
                final long voltage = energy.voltage();
                final byte tier = GTUtil.getTierByVoltage(voltage);
                final String name = tier >= 0 && tier < GTValues.VN.length ? GTValues.VN[tier] : "?";
                parts.append(voltage).append(unit).append(name);
                if (energy.amperage() > 1) {
                    parts.append(" x").append(energy.amperage()).append("A (").append(voltage * energy.amperage())
                            .append(" total)");
                }
                return;
            }
        }
    }

    private static boolean producesItem(SizedIngredient sized, Item item) {
        for (ItemStack produced : sized.getItems()) {
            if (produced.is(item)) {
                return true;
            }
        }
        return false;
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
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            LOGGER.info("[gttweaks]   {}", line);
            if (i < MAX_LINES) {
                source.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
            }
        }
        if (lines.size() > MAX_LINES) {
            source.sendSuccess(() -> Component.literal("  ... " + (lines.size() - MAX_LINES)
                    + " more in the server log").withStyle(ChatFormatting.GRAY), false);
        }
        return lines.size();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[gttweaks] recipematch: " + message));
        return 0;
    }
}
