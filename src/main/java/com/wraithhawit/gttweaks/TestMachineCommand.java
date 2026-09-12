package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code /gttweaks} commands: {@code testmachine [limit]} runs every recipe of the machine you
 * are looking at against that machine, in the world, and reports which ones it would refuse;
 * {@code sweep [type fragments]} runs {@link RecipeRunVerifier}'s synthetic-holder check without a
 * restart.
 *
 * <p>This is the half of the recipe check that {@link RecipeRunVerifier} cannot do. That one builds
 * a synthetic holder and so can sweep everything cheaply, at the cost of never seeing the machine:
 * no recipe modifiers, no parallels or overclocks, no coil bonus, no {@code checkConditions}, and
 * no real items — a custom ingredient there only ever matches the exemplar it hands out itself.
 * Here the machine is real, which means all of that is exercised, including the maintenance hatch
 * that a formed multiblock still needs before it will run anything.
 *
 * <p><b>Nothing is consumed.</b> Only {@code matchRecipe} and {@code checkConditions} are called,
 * both simulations; {@code handleRecipeIO}, the method that actually moves items, is never reached.
 * Even so the command refuses to start unless every bus and hatch is empty, and restores the
 * machine's stored energy afterwards. That is not politeness: a throw partway through a fill would
 * otherwise leave someone's items in a bus they did not put them in, and this runs on the server
 * thread where an exception aborts the rest of the sweep.
 *
 * <p>The sweep runs on the server thread, so it stops at {@code limit} recipes — 500 by default.
 * Raising it stalls the world for as long as the sweep takes, which for an Apiary II holding tens of
 * thousands of recipes is not a thing to do on a live server.
 *
 * <p><b>Reading a failure.</b> "rejected by the machine" means {@code fullModifyRecipe} returned
 * null — the machine itself refused the recipe before matching, which is where a coil-tier or
 * parallel requirement shows up. "no room" means the recipe needs more bus slots than the machine
 * has. A condition failure names the condition. Everything else is GregTech's own matcher, and the
 * reason it gives is the reason a player would see.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class TestMachineCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_LIMIT = 500;
    private static final int CHAT_FAILURES = 5;
    private static final double REACH = 12.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gttweaks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("testmachine")
                        .executes(context -> run(context, DEFAULT_LIMIT, 0))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20_000))
                                .executes(context ->
                                        run(context, IntegerArgumentType.getInteger(context, "limit"), 0))
                                .then(Commands.argument("offset", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(context -> run(context,
                                                IntegerArgumentType.getInteger(context, "limit"),
                                                IntegerArgumentType.getInteger(context, "offset"))))))
                .then(Commands.literal("sweep")
                        .executes(context -> sweep(context, List.of()))
                        .then(Commands.argument("types", StringArgumentType.greedyString())
                                .executes(context -> sweep(context,
                                        parseFilter(StringArgumentType.getString(context, "types")))))));
    }

    /**
     * {@code /gttweaks sweep [type fragments]} â the whole synthetic-holder check, in the running
     * world.
     *
     * <p>The same work {@link RecipeRunVerifier} does at server start behind a {@code -D} flag. It
     * is here because the flag needs a launcher edit and a restart, while the pack you actually
     * want to check is the one already loaded, with every mod's items in it. It blocks the server
     * thread for as long as the sweep takes â a few seconds for tens of thousands of recipes â so
     * narrow it with a fragment (\"/gttweaks sweep greenhouse,star_forge\") on a live server.
     */
    private static int sweep(CommandContext<CommandSourceStack> context, List<String> filter) {
        final CommandSourceStack source = context.getSource();
        final long start = System.currentTimeMillis();
        final List<String> summary = RecipeRunVerifier.sweepAll(source.getServer(), filter);
        for (String line : summary) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        final long elapsed = System.currentTimeMillis() - start;
        source.sendSuccess(() -> Component.literal("swept in " + elapsed + "ms")
                .withStyle(ChatFormatting.GRAY), false);
        return summary.size();
    }

    private static List<String> parseFilter(String argument) {
        final List<String> fragments = new ArrayList<>();
        for (String piece : argument.split("[,\s]+")) {
            final String trimmed = piece.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                fragments.add(trimmed);
            }
        }
        return fragments;
    }

    private static int run(CommandContext<CommandSourceStack> context, int limit, int offset) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Run this as a player, looking at the machine."));
            return 0;
        }

        final HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)) {
            source.sendFailure(Component.literal("Look at a GregTech machine and run it again."));
            return 0;
        }
        final MetaMachine machine = MetaMachine.getMachine(player.level(), block.getBlockPos());
        if (machine == null) {
            source.sendFailure(Component.literal("That block is not a GregTech machine."));
            return 0;
        }
        if (!(machine instanceof IRecipeLogicMachine logic)) {
            source.sendFailure(Component.literal(
                    machine.getDefinition().getId() + " has no recipe logic, so there is nothing to run."));
            return 0;
        }
        if (machine instanceof MultiblockControllerMachine controller && !controller.isFormed()) {
            source.sendFailure(Component.literal(
                    "That multiblock is not formed. Form it first — an unformed controller has no buses, "
                            + "so every recipe would 'fail' for the same uninteresting reason."));
            return 0;
        }

        final Buses buses = Buses.of(logic);
        if (!buses.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Empty every input and output bus, tank and hatch first. The check fills them itself "
                            + "and will not risk what is already in there."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(buses.summary(machine)).withStyle(ChatFormatting.GRAY), false);
        LOGGER.info("[gttweaks] testmachine {} - {}", machine.getDefinition().getId(), buses.summary(machine));
        final Report report = sweep(logic, buses, limit, offset);
        report.offset = offset;
        report.send(source, machine.getDefinition().getId().toString());
        return report.failed;
    }

    private static Report sweep(IRecipeLogicMachine logic, Buses buses, int limit, int offset) {
        final Report report = new Report();
        int index = 0;
        final Set<GTRecipeType> types = new LinkedHashSet<>();
        for (GTRecipeType type : logic.getRecipeTypes()) {
            if (type != null) {
                types.add(type);
            }
        }

        buses.rememberCircuits();
        final long[] storedEnergy = buses.chargeToFull();
        try {
            for (GTRecipeType type : types) {
                final List<GTRecipe> recipes = recipesOf(logic, type);
                report.available += recipes.size();
                for (GTRecipe recipe : recipes) {
                    // Skipped rather than sub-listed, so the numbering stays stable across a
                    // machine's several recipe types and an offset always means the same recipe.
                    if (index++ < offset) {
                        continue;
                    }
                    if (report.tested >= limit) {
                        break;
                    }
                    report.tested++;
                    try {
                        test(logic, buses, recipe, report);
                    } catch (Throwable t) {
                        report.fail(recipe, "threw " + t, LOGGER);
                    } finally {
                        buses.clear();
                    }
                }
            }
        } finally {
            buses.clear();
            buses.restoreEnergy(storedEnergy);
            buses.restoreCircuits();
        }
        return report;
    }

    private static List<GTRecipe> recipesOf(IRecipeLogicMachine logic, GTRecipeType type) {
        try {
            return logic.self().getLevel().getRecipeManager().getAllRecipesFor(type).stream()
                    .map(holder -> (GTRecipe) holder.value())
                    .toList();
        } catch (Throwable t) {
            LOGGER.error("[gttweaks] could not list recipes for {}: {}", type.registryName, t.toString());
            return List.of();
        }
    }

    private static void test(IRecipeLogicMachine logic, Buses buses, GTRecipe original, Report report) {
        final GTRecipe recipe;
        try {
            recipe = logic.fullModifyRecipe(original);
        } catch (Throwable t) {
            report.fail(original, "the machine's recipe modifier threw: " + t, LOGGER);
            return;
        }
        if (recipe == null) {
            report.fail(original, whyRejected(logic, original), LOGGER);
            return;
        }

        final String noRoom = buses.fill(recipe);
        if (noRoom != null) {
            report.fail(original, noRoom, LOGGER);
            return;
        }

        final ActionResult match = RecipeHelper.matchRecipe(logic, recipe);
        if (!match.isSuccess()) {
            report.fail(original, describe(match), LOGGER);
            return;
        }
        final ActionResult tick = RecipeHelper.matchTickRecipe(logic, recipe);
        if (!tick.isSuccess()) {
            report.fail(original, "per-tick: " + describe(tick), LOGGER);
            return;
        }
        final ActionResult conditions = RecipeHelper.checkConditions(recipe, logic.getRecipeLogic());
        if (!conditions.isSuccess()) {
            report.fail(original, "condition: " + describe(conditions), LOGGER);
            return;
        }
        report.matched++;
    }

    /**
     * Says which side of the machine came up short, in words.
     *
     * <p>GregTech's own text is accurate and unhelpful: "Insufficient Inputs: Item" leaves you to
     * work out that a bus is short, while "Insufficient Outputs: GTCEu Energy" means the machine
     * wants a dynamo. The capability and IO already carry that, so the plain phrase comes first and
     * GregTech's wording follows in brackets for anyone matching it against the game's own UI.
     */
    private static String describe(ActionResult result) {
        final RecipeCapability<?> capability = result.capability();
        final IO io = result.io();
        final boolean out = io == IO.OUT;
        final String phrase;
        if (capability == GTRecipeCapabilities.ITEM) {
            phrase = out ? "no room for the item output" : "not enough item input";
        } else if (capability == GTRecipeCapabilities.FLUID) {
            phrase = out ? "no room for the fluid output" : "not enough fluid input";
        } else if (capability == GTRecipeCapabilities.EU) {
            phrase = out ? "no room for the power output - needs a dynamo hatch" : "not enough power input";
        } else if (capability == GTRecipeCapabilities.CWU) {
            phrase = out ? "no room for the computation output" : "not enough computation";
        } else if (capability == null) {
            phrase = "the machine has no handler for what this recipe needs";
        } else {
            phrase = "short on " + capability.id + " " + io;
        }
        final String gregtech = result.reason() == null ? "" : result.reason().getString();
        return gregtech.isBlank() ? phrase : phrase + " (" + gregtech + ")";
    }

    /**
     * Which recipe modifier refused the recipe, and why it says it did.
     *
     * <p>{@code fullModifyRecipe} returning null is the machine saying no before matching ever
     * happens, and on its own it names neither the cause nor the culprit â every Star Forge recipe
     * failing that way says only "something about this machine". But the chain is walkable:
     * {@code MachineDefinition.getRecipeModifier()} is a {@code RecipeModifierList} whose
     * {@code getModifiers()} is public, and every {@code ModifierFunction} carries a
     * {@code getFailReason()} that GregTech wrote for exactly this. So ask each modifier in turn
     * which one produces a null function, and print its own words.
     */
    /**
     * The modifiers, by the constant they were declared as.
     *
     * <p>Every entry in {@code GTRecipeModifiers} is a lambda, so {@code getSimpleName()} reports
     * "GTRecipeModifiers" for all of them and tells you nothing - which is exactly how far the
     * first version of this got on the Star Forge. Reflecting the class's own public constants once
     * and matching by identity gives back the name the KubeJS script actually wrote.
     */
    private static Map<RecipeModifier, String> modifierNames;

    /**
     * Built on first use, and <b>never</b> from a static initializer.
     *
     * <p>This class is an {@code @EventBusSubscriber}, so NeoForge loads it during mod
     * loading. Reflecting {@code GTRecipeModifiers} forces that class to initialize, and its
     * own clinit reads {@code GTMedicalConditions.CARBON_MONOXIDE_POISONING} - which
     * constructs a MedicalCondition long before GregTech registers them. The stray instance
     * then fails the datapack registry load with "Unregistered holder in
     * gtceu:medical_condition", the world refuses to open, and Minecraft offers safe mode.
     * Shipped that way in 0.12.0. A diagnostic that stops the game from starting is worse
     * than no diagnostic, so this stays lazy: by the time a command runs, everything is
     * registered.
     */
    private static Map<RecipeModifier, String> modifierNames() {
        if (modifierNames == null) {
            modifierNames = knownModifiers();
        }
        return modifierNames;
    }

    private static Map<RecipeModifier, String> knownModifiers() {
        final Map<RecipeModifier, String> names = new IdentityHashMap<>();
        try {
            for (Field field : GTRecipeModifiers.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && RecipeModifier.class.isAssignableFrom(field.getType())
                        && field.get(null) instanceof RecipeModifier modifier) {
                    names.put(modifier, field.getName());
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[gttweaks] could not read the GTRecipeModifiers constants: {}", t.toString());
        }
        return names;
    }

    private static String whyRejected(IRecipeLogicMachine logic, GTRecipe recipe) {
        final MetaMachine machine = logic.self();

        // fullModifyRecipe is two stages - the modifier chain, then output trimming against the
        // machine's own limits. Ask which one nulled before blaming a modifier for either.
        GTRecipe modified;
        try {
            modified = logic.doModifyRecipe(recipe);
        } catch (Throwable t) {
            return "a recipe modifier threw while the machine considered it: " + t;
        }
        if (modified != null) {
            return "the modifiers accepted it, then output trimming rejected it - the machine's "
                    + "getOutputLimits() will not carry this recipe's outputs";
        }
        return whyModifierRejected(machine, recipe);
    }

    private static String whyModifierRejected(MetaMachine machine, GTRecipe recipe) {

        final RecipeModifier modifier = machine.getDefinition().getRecipeModifier();
        final List<RecipeModifier> chain = modifier instanceof RecipeModifierList list
                ? Arrays.asList(list.getModifiers())
                : List.of(modifier);
        for (int i = 0; i < chain.size(); i++) {
            final RecipeModifier step = chain.get(i);
            final String name = modifierName(step) + " (" + (i + 1) + " of " + chain.size() + ")";
            final ModifierFunction function;
            try {
                function = step.getModifier(machine, recipe);
            } catch (Throwable t) {
                return name + " threw while deciding: " + t;
            }
            if (function == null) {
                return name + " handed back no modifier at all" + tierNote(machine, recipe);
            }
            final String why = function.getFailReason() == null ? "" : function.getFailReason().getString();
            if (function == ModifierFunction.NULL) {
                return name + " refused the recipe outright"
                        + (why.isBlank() ? "" : " (" + why + ")") + tierNote(machine, recipe);
            }
            final GTRecipe result;
            try {
                result = function.apply(recipe);
            } catch (Throwable t) {
                return name + " threw while applying itself to the recipe: " + t;
            }
            if (result == null) {
                // FunctionBuilder runs apply through applySafe, which catches whatever went wrong
                // and returns null. So this is not the modifier objecting - it is the modifier
                // breaking, silently, and the text below is only its default failure string.
                return name + " produced no recipe when applied - GregTech swallowed the error inside "
                        + "applySafe" + (why.isBlank() ? "" : " (" + why + ")") + tierNote(machine, recipe);
            }
        }
        return "no single modifier refused it, yet the chain as a whole produced nothing - they "
                + "only fail in combination" + tierNote(machine, recipe);
    }

    /** The constant's own name where we know it, else a trimmed class name. */
    private static String modifierName(RecipeModifier modifier) {
        final String known = modifierNames().get(modifier);
        if (known != null) {
            return known;
        }
        String name = modifier.getClass().getSimpleName();
        final int lambda = name.indexOf("$$Lambda");
        if (lambda > 0) {
            name = name.substring(0, lambda);
        }
        return name.isBlank() ? modifier.getClass().getName() : name;
    }

    /** The two numbers an overclock decision turns on, appended wherever one is suspected. */
    private static String tierNote(MetaMachine machine, GTRecipe recipe) {
        try {
            if (machine instanceof IOverclockMachine overclock) {
                return " [recipe tier " + RecipeHelper.getRecipeEUtTier(recipe)
                        + ", machine max overclock tier " + overclock.getMaxOverclockTier() + "]";
            }
        } catch (Throwable ignored) {
            // A diagnostic must never be the thing that fails.
        }
        return "";
    }

    /**
     * The machine's own handlers, gathered once.
     *
     * <p>Both {@code IO.IN} and {@code IO.BOTH} are collected: a combined bus registers under BOTH
     * and is a perfectly good place for a recipe's inputs to come from, and a machine that has only
     * those would otherwise look like it had no buses at all.
     */
    private static final class Buses {

        private final List<NotifiableItemStackHandler> items = new ArrayList<>();
        private final List<NotifiableFluidTank> fluids = new ArrayList<>();
        private final List<NotifiableItemStackHandler> outputItems = new ArrayList<>();
        private final List<NotifiableFluidTank> outputFluids = new ArrayList<>();
        private final List<NotifiableEnergyContainer> energy = new ArrayList<>();
        /**
         * The bus's programmed-circuit slots, kept apart from {@link #items}.
         *
         * <p>A circuit is not a bus slot. {@code ItemBusPartMachine} holds a
         * {@code ProgrammableCircuitSlotTrait}, which is a recipe handler but not a
         * {@code NotifiableItemStackHandler}, so it never appeared in the item list and the
         * fill spent a real inventory slot on the circuit instead. That is invisible until a
         * recipe fills every slot it has: charcoal_to_coal_tar_superheated wants 32,000
         * charcoal, exactly 500 slots, and the circuit was then reported as homeless on a
         * machine that would have run it perfectly well in game.
         */
        private final List<ProgrammableCircuitSlotTrait> circuits = new ArrayList<>();
        private final List<ItemStack> circuitsBefore = new ArrayList<>();
        /** Set when a bus accepts a write and hands back nothing - the ME stocking bus signature. */
        private boolean writeRejected;

        static Buses of(IRecipeLogicMachine logic) {
            final Buses buses = new Buses();
            collect(logic, IO.IN, GTRecipeCapabilities.ITEM, NotifiableItemStackHandler.class, buses.items);
            collect(logic, IO.BOTH, GTRecipeCapabilities.ITEM, NotifiableItemStackHandler.class, buses.items);
            collect(logic, IO.IN, GTRecipeCapabilities.FLUID, NotifiableFluidTank.class, buses.fluids);
            collect(logic, IO.BOTH, GTRecipeCapabilities.FLUID, NotifiableFluidTank.class, buses.fluids);
            collect(logic, IO.OUT, GTRecipeCapabilities.ITEM, NotifiableItemStackHandler.class, buses.outputItems);
            collect(logic, IO.OUT, GTRecipeCapabilities.FLUID, NotifiableFluidTank.class, buses.outputFluids);
            collect(logic, IO.IN, GTRecipeCapabilities.ITEM, ProgrammableCircuitSlotTrait.class, buses.circuits);
            collect(logic, IO.BOTH, GTRecipeCapabilities.ITEM, ProgrammableCircuitSlotTrait.class, buses.circuits);
            collect(logic, IO.IN, GTRecipeCapabilities.EU, NotifiableEnergyContainer.class, buses.energy);
            collect(logic, IO.BOTH, GTRecipeCapabilities.EU, NotifiableEnergyContainer.class, buses.energy);
            return buses;
        }

        private static <T> void collect(IRecipeLogicMachine logic, IO io, RecipeCapability<?> capability,
                                        Class<T> type, List<T> into) {
            for (IRecipeHandler<?> handler : logic.getCapabilitiesFlat(io, capability)) {
                if (type.isInstance(handler) && !into.contains(type.cast(handler))) {
                    into.add(type.cast(handler));
                }
            }
        }

        boolean isEmpty() {
            for (NotifiableItemStackHandler handler : items) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (!handler.getStackInSlot(slot).isEmpty()) {
                        return false;
                    }
                }
            }
            for (NotifiableItemStackHandler handler : outputItems) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (!handler.getStackInSlot(slot).isEmpty()) {
                        return false;
                    }
                }
            }
            for (NotifiableFluidTank tank : fluids) {
                for (int index = 0; index < tank.getTanks(); index++) {
                    if (!tank.getFluidInTank(index).isEmpty()) {
                        return false;
                    }
                }
            }
            for (NotifiableFluidTank tank : outputFluids) {
                for (int index = 0; index < tank.getTanks(); index++) {
                    if (!tank.getFluidInTank(index).isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Returns null when everything fit, or the reason it did not. */
        String fill(GTRecipe recipe) {
            // Per recipe: a rejected write on one recipe must not read as a rejected write on
            // every recipe after it.
            writeRejected = false;
            for (Content content : recipe.getInputContents(GTRecipeCapabilities.ITEM)) {
                if (!(content.content() instanceof SizedIngredient sized)) {
                    continue;
                }
                final ItemStack[] candidates = sized.getItems();
                if (candidates.length == 0 || candidates[0].isEmpty()) {
                    return "an item input matches nothing at all (empty tag or unresolved id)";
                }
                if (sized.ingredient().getCustomIngredient() instanceof IntCircuitIngredient
                        && !circuits.isEmpty()) {
                    // Preferred route: the bus's own circuit configurator, which costs no inventory
                    // slot. A recipe whose other inputs fill every slot the machine has would
                    // otherwise report its circuit as homeless.
                    circuits.get(0).storage.setStackInSlot(0, candidates[0].copy());
                    continue;
                }
                // Otherwise the circuit goes in as an ordinary item, which is the other way a player
                // can satisfy one: a real programmed circuit sitting in a bus slot matches the
                // recipe and is never consumed. Only reached when the machine has no configurator.
                int remaining = Math.max(sized.count(), 1);
                final int perSlot = Math.max(candidates[0].getMaxStackSize(), 1);
                while (remaining > 0) {
                    final ItemStack portion = candidates[0].copy();
                    portion.setCount(Math.min(remaining, perSlot));
                    if (!place(portion)) {
                        return writeRejected
                                ? "the input bus would not take a direct write, so this recipe cannot be "
                                        + "tested here - an ME bus serves its slots from the network. Test it "
                                        + "by running it, or use a plain bus."
                                : "no room: the input buses cannot hold " + sized.count() + "x "
                                        + candidates[0].getHoverName().getString();
                    }
                    remaining -= portion.getCount();
                }
            }
            for (Content content : recipe.getInputContents(GTRecipeCapabilities.FLUID)) {
                if (!(content.content() instanceof SizedFluidIngredient sized)) {
                    continue;
                }
                final FluidStack[] candidates = sized.getFluids();
                if (candidates.length == 0 || candidates[0].isEmpty()) {
                    return "a fluid input matches nothing at all";
                }
                final FluidStack fluid = candidates[0].copy();
                fluid.setAmount(Math.max(sized.amount(), 1));
                if (!place(fluid)) {
                    return writeRejected
                            ? "the input hatch would not take a direct write, so this recipe cannot be "
                                    + "tested here - an ME hatch serves its tanks from the network."
                            : "not enough fluid capacity: the input tanks hold " + fluidCapacity()
                                    + "mB in total, and this recipe needs " + fluid.getAmount()
                                    + "mB of " + fluid.getHoverName().getString();
                }
            }
            return null;
        }

        /**
         * Writes one stack into the first free slot, and <b>checks it landed</b>.
         *
         * <p>The read-back is not paranoia. An ME stocking bus serves its slots from the AE network,
         * so {@code setStackInSlot} goes nowhere and the slot still reads empty; without this check
         * the fill walks the same slot forever, reports success, and GregTech is left to say
         * "Insufficient Inputs" about items this command never actually delivered. That produced a
         * false accusation against three Micro Universe Orb recipes on 2026-09-08.
         */
        private boolean place(ItemStack stack) {
            for (NotifiableItemStackHandler handler : items) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (handler.getStackInSlot(slot).isEmpty()) {
                        handler.setStackInSlot(slot, stack);
                        final ItemStack landed = handler.getStackInSlot(slot);
                        if (landed.isEmpty() || landed.getCount() < stack.getCount()) {
                            writeRejected = true;
                        }
                        return !writeRejected;
                    }
                }
            }
            return false;
        }

        /**
         * Spreads one fluid ingredient across as many tanks as it takes.
         *
         * <p>The first version demanded a single tank big enough for the whole amount, which is not
         * how GregTech matches: a recipe's fluid is summed across every input tank the machine has,
         * exactly as items are summed across bus slots. The Star Forge's infinite_* recipes want
         * 10,000,000 mB each, and the tool reported "no room" identically at 5 tanks and at 20 -
         * the number that should have moved and did not, which is what gave the bug away.
         */
        private boolean place(FluidStack fluid) {
            int remaining = fluid.getAmount();
            for (NotifiableFluidTank tank : fluids) {
                for (int index = 0; index < tank.getTanks() && remaining > 0; index++) {
                    if (!tank.getFluidInTank(index).isEmpty()) {
                        continue;
                    }
                    final int portion = Math.min(remaining, tank.getTankCapacity(index));
                    if (portion <= 0) {
                        continue;
                    }
                    final FluidStack part = fluid.copy();
                    part.setAmount(portion);
                    tank.setFluidInTank(index, part);
                    final FluidStack landed = tank.getFluidInTank(index);
                    if (landed.isEmpty() || landed.getAmount() < portion) {
                        writeRejected = true;
                        return false;
                    }
                    remaining -= portion;
                }
            }
            return remaining <= 0;
        }

        /** Total input tank capacity, so a shortfall is reported as a number you can build against. */
        private long fluidCapacity() {
            long capacity = 0;
            for (NotifiableFluidTank tank : fluids) {
                for (int index = 0; index < tank.getTanks(); index++) {
                    capacity += tank.getTankCapacity(index);
                }
            }
            return capacity;
        }

        void clear() {

            for (NotifiableItemStackHandler handler : items) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    handler.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            for (NotifiableFluidTank tank : fluids) {
                for (int index = 0; index < tank.getTanks(); index++) {
                    tank.setFluidInTank(index, FluidStack.EMPTY);
                }
            }
        }

        /**
         * Fills every energy hatch and hands back what each one held, per container rather than as
         * one number: restoring a single figure to all of them would quietly mint energy into the
         * emptier hatches of a machine that has several.
         */
        /**
         * What the machine actually brings to a recipe, in one line.
         *
         * <p>Printed before the results because most failures are answered by it: "not enough power
         * input" means little until you can see the machine is capped at UV, and "no room for the
         * item output" means little without the slot count next to it.
         */
        String summary(MetaMachine machine) {
            final StringBuilder text = new StringBuilder();
            if (machine instanceof WorkableElectricMultiblockMachine electric) {
                final long max = electric.getMaxVoltage();
                text.append("max ").append(max).append(" EU/t (")
                        .append(GTValues.VN[GTUtil.getFloorTierByVoltage(max)]).append("), ");
            }
            text.append(energy.size()).append(" energy hatch(es), ");
            text.append(slotCount(items)).append(" input slots, ")
                    .append(slotCount(outputItems)).append(" output slots, ");
            text.append(tankCount(fluids)).append(" input tanks (")
                    .append(fluidCapacity()).append("mB total), ")
                    .append(tankCount(outputFluids)).append(" output tanks");
            text.append(", ").append(circuits.size()).append(" circuit slot(s)");
            if (machine instanceof MultiblockControllerMachine controller) {
                // Named because a parallel hatch silently multiplies every recipe, and a
                // multiplied recipe is a different recipe than the one you are reading in EMI.
                text.append(controller.getParallelHatch().isPresent()
                        ? ", PARALLEL HATCH present"
                        : ", no parallel hatch");
            }
            return text.toString();
        }

        private static int slotCount(List<NotifiableItemStackHandler> handlers) {
            int slots = 0;
            for (NotifiableItemStackHandler handler : handlers) {
                slots += handler.getSlots();
            }
            return slots;
        }

        private static int tankCount(List<NotifiableFluidTank> tanks) {
            int count = 0;
            for (NotifiableFluidTank tank : tanks) {
                count += tank.getTanks();
            }
            return count;
        }

        /** Circuits are put back exactly as found; a set circuit is someone's configuration. */
        void rememberCircuits() {
            circuitsBefore.clear();
            for (ProgrammableCircuitSlotTrait circuit : circuits) {
                circuitsBefore.add(circuit.storage.getStackInSlot(0).copy());
            }
        }

        void restoreCircuits() {
            for (int i = 0; i < circuits.size() && i < circuitsBefore.size(); i++) {
                circuits.get(i).storage.setStackInSlot(0, circuitsBefore.get(i));
            }
        }

        long[] chargeToFull() {

            final long[] stored = new long[energy.size()];
            for (int i = 0; i < energy.size(); i++) {
                final NotifiableEnergyContainer container = energy.get(i);
                stored[i] = container.getEnergyStored();
                container.setEnergyStored(container.getEnergyCapacity());
            }
            return stored;
        }

        void restoreEnergy(long[] stored) {
            for (int i = 0; i < energy.size() && i < stored.length; i++) {
                energy.get(i).setEnergyStored(stored[i]);
            }
        }
    }

    private static final class Report {

        private int available;
        private int tested;
        private int matched;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        void fail(GTRecipe recipe, String reason, Logger logger) {
            failed++;
            final String line = recipe.getId() + ": " + reason;
            failures.add(line);
            logger.error("[gttweaks] TESTMACHINE FAILED {}", line);
        }

        private int offset;

        void send(CommandSourceStack source, String machineId) {
            final String headline = machineId + " — " + matched + " of " + tested + " recipes would run, "
                    + failed + " would not";
            source.sendSuccess(() -> Component.literal(headline)
                    .withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            final int next = offset + tested;
            if (next < available) {
                source.sendSuccess(() -> Component.literal(
                        "Tested " + offset + ".." + (next - 1) + " of " + available
                                + " - continue with: /gttweaks testmachine " + Math.max(tested, 1)
                                + " " + next).withStyle(ChatFormatting.GRAY), false);
            } else if (offset > 0) {
                source.sendSuccess(() -> Component.literal(
                        "Tested " + offset + ".." + (next - 1) + " of " + available
                                + " - end of the list.").withStyle(ChatFormatting.GRAY), false);
            }
            for (int i = 0; i < Math.min(failures.size(), CHAT_FAILURES); i++) {
                final String line = failures.get(i);
                source.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.RED), false);
            }
            if (failures.size() > CHAT_FAILURES) {
                source.sendSuccess(() -> Component.literal(
                        "  ...and " + (failures.size() - CHAT_FAILURES)
                                + " more; all of them are in the log as TESTMACHINE FAILED.")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            LOGGER.info("[gttweaks] testmachine {}: {} matched, {} failed, {} tested of {} available",
                    machineId, matched, failed, tested, available);
        }
    }
}
