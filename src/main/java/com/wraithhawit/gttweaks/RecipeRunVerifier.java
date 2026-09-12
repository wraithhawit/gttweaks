package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs every GregTech recipe through GregTech's own matcher against a synthetic machine, so a
 * recipe that can never be crafted is found by a log line instead of by building the multiblock.
 *
 * <p>{@link RecipeVerifier} answers "did this recipe register"; this answers "would the machine
 * accept it". Those are different questions, and the port turned up several recipes that passed the
 * first and would have failed the second — an empty {@code #forge:} tag registers a recipe with a
 * blank slot that nothing can fill, and a machine whose recipe type caps outputs at 19 will not run
 * a recipe carrying 20.
 *
 * <p><b>How.</b> {@code RecipeHelper.matchRecipe} takes an {@code IRecipeCapabilityHolder}, an
 * interface with two map-shaped methods and no tie to a block entity, and simulates both the input
 * draw and the output insert. A holder is built per recipe out of {@link DetachedHandlers} — real
 * GregTech handlers with no machine — stocked with exactly what the recipe asks for. A recipe that
 * cannot match a machine holding precisely its own inputs is broken in a way no amount of playing
 * would fix.
 *
 * <p><b>What this cannot tell you, and it matters.</b> Stocking a holder from a recipe's own
 * ingredients is close to circular: it proves the ingredient matches the exemplar the ingredient
 * itself hands out, not that it matches the item a player would put in the bus. For a plain item or
 * tag those are the same stack. For a <i>custom</i> ingredient — {@code gttweaks:partial_data}, a
 * component-matching ingredient like {@code hostilenetworks:data_model[...]} — they are not: the
 * real item carries components the exemplar does not, and only a real item settles it. Those
 * recipes are counted and listed separately, and they are what {@code /gttweaks testmachine} is
 * for.
 *
 * <p>Also out of reach here: the machine's recipe modifiers (parallels, overclocks, coil bonuses),
 * {@code checkConditions}, and computation (CWU) inputs, which need a real {@code RecipeLogic}.
 * Recipes using those are skipped with a reason rather than being counted as passes.
 *
 * <p><b>Recipes no ordinary bus can feed are reported, not matched.</b> ATM9's greenhouse and
 * micro universe collector ask for six-figure item counts in a single recipe - 655,360 essence, for
 * one - which only GregTech's ME stocking bus can present. Stocking that truthfully means ten
 * thousand slots and as many item copies, measured at five to seven seconds per recipe, and the
 * cheap shortcut is worse than useless: one oversized stack yields a single stack size per slot to
 * NeoForge's extractItem, so the recipe fails for a reason that belongs entirely to this check.
 * They are counted as skipped, with the amount and the slot count in the log.
 *
 * <p><b>The negative control.</b> Every recipe with something to withhold is also matched against
 * an identically-shaped but <i>empty</i> holder, which must fail. A harness that says yes to
 * everything would otherwise report a clean sweep, which is the failure mode this whole check
 * exists to avoid. The empty-holder count is printed next to the pass count on purpose: read them
 * as a pair.
 *
 * <p>Off unless {@code -Dgttweaks.runRecipes} is set. {@code true} sweeps everything; a
 * comma-separated list restricts it to recipe types whose id contains one of the entries, e.g.
 * {@code -Dgttweaks.runRecipes=greenhouse,star_forge}.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeRunVerifier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.runRecipes";

    /** Enough energy that no recipe fails for want of it; small enough to leave long arithmetic alone. */
    private static final long ENERGY = 1_000_000_000_000L;
    /** Past this many bus slots a recipe is reported and left unmatched; see stock(). */
    private static final int BUS_SLOTS = 1024;
    /** Output room is sized to fit, not to judge; past this the recipe is reported, not matched. */
    private static final int MAX_OUTPUT_SLOTS = 65_536;
    /** Per recipe type, so one broken script cannot bury the rest of the run in its own output. */
    private static final int MAX_REPORTS_PER_TYPE = 10;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event) {
        final List<String> filter = filter();
        if (filter == null) {
            return;
        }
        sweepAll(event.getServer(), filter);
    }

    /**
     * The whole sweep, callable without a restart.
     *
     * <p>Also reachable as {@code /gttweaks sweep}, which is how the real pack gets checked: adding
     * a {@code -D} flag to a launcher profile and restarting to read a log is a poor loop next to
     * running it in the world that already has every mod's items loaded. The lines it returns are
     * the same lines it logs.
     */
    public static List<String> sweepAll(MinecraftServer server, List<String> filter) {
        final Map<GTRecipeType, String> types = recipeTypes(filter);
        final Totals totals = new Totals();

        for (Map.Entry<GTRecipeType, String> entry : types.entrySet()) {
            sweep(server, entry.getKey(), entry.getValue(), totals);
        }

        final List<String> summary = new ArrayList<>();
        summary.add(types.size() + " recipe types, " + totals.seen + " recipes: " + totals.matched
                + " matched, " + totals.failed + " FAILED, " + totals.skipped + " skipped, "
                + totals.custom + " self-match only");
        summary.add("negative control: " + totals.refusedEmpty + " of " + totals.negativeTested
                + " refused an empty machine");
        if (totals.negativeTested > 0 && totals.refusedEmpty < totals.negativeTested) {
            summary.add("CONTROL FAILED - read nothing above as a pass");
        }
        summary.add("details are in the log: RECIPE FAILED, RECIPE WARNING");

        LOGGER.info("[gttweaks] recipe run: {} types, {} recipes, {} matched, {} FAILED, {} skipped, "
                + "{} self-match only (custom ingredient)",
                types.size(), totals.seen, totals.matched, totals.failed, totals.skipped, totals.custom);
        LOGGER.info("[gttweaks] negative control: {} of {} testable recipes correctly refused an empty machine",
                totals.refusedEmpty, totals.negativeTested);
        if (totals.negativeTested > 0 && totals.refusedEmpty < totals.negativeTested) {
            LOGGER.error("[gttweaks] CONTROL FAILED: {} recipe(s) matched a machine holding nothing. "
                    + "Either those recipes have no real inputs, or this check is not checking anything — "
                    + "resolve that before believing the pass count above.",
                    totals.negativeTested - totals.refusedEmpty);
        }
        if (totals.failed > 0) {
            LOGGER.error("[gttweaks] {} recipe(s) cannot be run by their own machine", totals.failed);
        }
        return summary;
    }

    /** Null when the check is off; empty for "everything"; otherwise the id fragments to keep. */
    private static List<String> filter() {
        final String setting = System.getProperty(PROPERTY);
        if (setting == null || setting.isBlank() || setting.equalsIgnoreCase("false")) {
            return null;
        }
        if (setting.equalsIgnoreCase("true")) {
            return List.of();
        }
        return Arrays.stream(setting.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * The recipe types reachable from a machine, mapped to a machine that uses each.
     *
     * <p>Walked through {@code GTRegistries.MACHINES} rather than {@code GTRegistries.RECIPE_TYPES}
     * for the reason {@link RecipeVerifier} records: GregTech's own types are not in that registry,
     * only the ones KubeJS creates, so iterating it would silently sweep almost nothing.
     */
    private static Map<GTRecipeType, String> recipeTypes(List<String> filter) {
        final Map<GTRecipeType, String> types = new LinkedHashMap<>();
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            final GTRecipeType[] machineTypes = definition.getRecipeTypes();
            if (machineTypes == null) {
                continue;
            }
            for (GTRecipeType type : machineTypes) {
                if (type == null || types.containsKey(type)) {
                    continue;
                }
                final String id = String.valueOf(type.registryName);
                if (filter.isEmpty() || filter.stream().anyMatch(id.toLowerCase(Locale.ROOT)::contains)) {
                    types.put(type, definition.getId() + " uses " + id);
                }
            }
        }
        return types;
    }

    private static void sweep(MinecraftServer server, GTRecipeType type, String label, Totals totals) {
        final List<GTRecipe> recipes;
        try {
            recipes = server.getRecipeManager().getAllRecipesFor(type).stream()
                    .map(holder -> (GTRecipe) holder.value())
                    .toList();
        } catch (Throwable t) {
            LOGGER.error("[gttweaks] RECIPE TYPE UNREADABLE {}: {}", type.registryName, t.toString());
            return;
        }
        if (recipes.isEmpty()) {
            return;
        }

        int matched = 0;
        int failed = 0;
        int skipped = 0;
        int custom = 0;
        int reported = 0;

        for (GTRecipe recipe : recipes) {
            totals.seen++;
            final Result result;
            try {
                result = check(recipe, type, totals);
            } catch (Throwable t) {
                totals.failed++;
                failed++;
                if (reported++ < MAX_REPORTS_PER_TYPE) {
                    LOGGER.error("[gttweaks] RECIPE THREW {} [{}]: {}", recipe.getId(), type.registryName, t.toString());
                }
                continue;
            }
            switch (result.kind) {
                case MATCHED -> {
                    matched++;
                    totals.matched++;
                    if (result.customIngredient) {
                        custom++;
                        totals.custom++;
                    }
                }
                case SKIPPED -> {
                    skipped++;
                    totals.skipped++;
                }
                case FAILED -> {
                    failed++;
                    totals.failed++;
                    if (reported++ < MAX_REPORTS_PER_TYPE) {
                        LOGGER.error("[gttweaks] RECIPE FAILED {} [{}]: {}",
                                recipe.getId(), type.registryName, result.reason);
                    }
                }
            }
            for (String warning : result.warnings) {
                LOGGER.warn("[gttweaks] RECIPE WARNING {} [{}]: {}", recipe.getId(), type.registryName, warning);
            }
        }

        if (reported > MAX_REPORTS_PER_TYPE) {
            LOGGER.error("[gttweaks] ...and {} more failure(s) in {}", reported - MAX_REPORTS_PER_TYPE, type.registryName);
        }
        LOGGER.info("[gttweaks] ran {} -> {} matched, {} failed, {} skipped, {} self-match only ({})",
                type.registryName, matched, failed, skipped, custom, label);
    }

    private static Result check(GTRecipe recipe, GTRecipeType type, Totals totals) {
        final List<String> warnings = new ArrayList<>();
        capacityWarnings(recipe, type, warnings);

        for (RecipeCapability<?> capability : recipe.inputs.keySet()) {
            if (capability != GTRecipeCapabilities.ITEM
                    && capability != GTRecipeCapabilities.FLUID
                    && capability != GTRecipeCapabilities.EU) {
                return Result.skipped(warnings);
            }
        }
        for (RecipeCapability<?> capability : recipe.tickInputs.keySet()) {
            if (capability != GTRecipeCapabilities.EU) {
                // Computation and block-state inputs need a real machine standing in a real world.
                return Result.skipped(warnings);
            }
        }

        final Stock stock = stock(recipe);
        warnings.addAll(stock.warnings);
        if (stock.tooBig != null) {
            warnings.add(stock.tooBig);
            return Result.skipped(warnings);
        }
        if (stock.problem != null) {
            return Result.failed(stock.problem, warnings);
        }

        // One holder for both calls: matching is a simulation and leaves the handlers as it found
        // them, and building a second copy of a stocked bus is the expensive half of this check.
        final Holder stocked = holder(recipe, stock, true);
        final ActionResult match = RecipeHelper.matchRecipe(stocked, recipe);
        if (!match.isSuccess()) {
            return Result.failed(describe(match) + contents(recipe, match), warnings);
        }
        final ActionResult tick = RecipeHelper.matchTickRecipe(stocked, recipe);
        if (!tick.isSuccess()) {
            return Result.failed("per-tick: " + describe(tick) + contents(recipe, tick), warnings);
        }

        if (stock.hasSomethingToWithhold) {
            totals.negativeTested++;
            final ActionResult empty = RecipeHelper.matchRecipe(holder(recipe, stock, false), recipe);
            if (empty.isSuccess()) {
                warnings.add("matched a machine holding NOTHING - its inputs are not really required");
            } else {
                totals.refusedEmpty++;
            }
        }

        return Result.matched(stock.customIngredient, warnings);
    }

    /**
     * Whether a custom ingredient is one whose exemplar really is the item a player would use.
     *
     * <p>The point of counting custom ingredients is to find the ones a self-match cannot vouch
     * for: a component matcher accepts an item carrying components its exemplar does not have.
     * GregTech's programmed circuit and its ranged-count ingredient are not like that â the circuit
     * exemplar is the circuit â and counting them would bury the handful that matter under
     * thousands that do not. Every recipe built with .circuit(n) uses one.
     */
    private static boolean selfDescribing(ICustomIngredient custom) {
        return custom instanceof IntCircuitIngredient || custom instanceof IntProviderIngredient;
    }

    /**
     * Flags a recipe that asks for more entries
 than its own recipe type declared through
     * {@code setMaxIOSize}. GregTech will not show or run it, but nothing at load time says so.
     */
    private static void capacityWarnings(GTRecipe recipe, GTRecipeType type, List<String> warnings) {
        capacityWarning(recipe, type, GTRecipeCapabilities.ITEM, "item", warnings);
        capacityWarning(recipe, type, GTRecipeCapabilities.FLUID, "fluid", warnings);
    }

    private static void capacityWarning(GTRecipe recipe, GTRecipeType type,
                                        RecipeCapability<?> capability, String name, List<String> warnings) {
        final int inputs = recipe.getInputContents(capability).size();
        final int outputs = recipe.getOutputContents(capability).size();
        if (inputs > type.getMaxInputs(capability)) {
            warnings.add(inputs + " " + name + " inputs but the recipe type allows "
                    + type.getMaxInputs(capability));
        }
        if (outputs > type.getMaxOutputs(capability)) {
            warnings.add(outputs + " " + name + " outputs but the recipe type allows "
                    + type.getMaxOutputs(capability));
        }
    }

    /** What the recipe asks for, resolved to concrete stacks, or the reason it cannot be. */
    private static final class Stock {
        private final List<ItemStack> items = new ArrayList<>();
        private final List<FluidStack> fluids = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String tooBig;
        private int slots;
        private int outputItemSlots;
        private int outputFluidTanks;
        private int fluidCapacity = 1;
        private boolean customIngredient;
        private boolean hasSomethingToWithhold;
        private String problem;
    }

    private static Stock stock(GTRecipe recipe) {
        final Stock stock = new Stock();

        for (Content content : recipe.getInputContents(GTRecipeCapabilities.ITEM)) {
            if (!(content.content() instanceof SizedIngredient sized)) {
                continue;
            }
            final ICustomIngredient custom = sized.ingredient().getCustomIngredient();
            if (custom != null && !selfDescribing(custom)) {
                stock.customIngredient = true;
            }
            final ItemStack[] items = sized.getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                stock.problem = "an item input matches nothing at all (empty tag or unresolved id): "
                        + sized.ingredient();
                return stock;
            }
            int remaining = Math.max(sized.count(), 1);
            // Per the item's own stack size, never a flat 64: Ender Pearls and Snowballs stack to
            // 16, and assuming 64 under-provisions those recipes fourfold — which reads as a broken
            // recipe when it is a broken harness.
            final int perSlot = Math.max(items[0].getMaxStackSize(), 1);
            final int needed = (remaining + perSlot - 1) / perSlot;
            if (needed > BUS_SLOTS) {
                // Not matched, and deliberately so. Stocking it truthfully means building 10,240
                // slots and copying 655,360 items, which measured at five to seven seconds per
                // recipe; and the cheap alternative is wrong, because NeoForge's
                // ItemStackHandler.extractItem clamps a draw to one stack size per slot, so a single
                // slot holding 655,360 yields 64 and the recipe "fails" for a reason that is purely
                // this harness. What is left to prove would be arithmetic. The amount is reported
                // instead: only GregTech's ME stocking bus can present a stack like this, and an
                // ordinary bus never will.
                stock.tooBig = "needs " + remaining + "x " + items[0].getHoverName().getString()
                        + " in one recipe (" + needed + " bus slots), so only an ME stocking bus can "
                        + "feed it - not matched, see the class notes";
                return stock;
            }
            while (remaining > 0) {
                final ItemStack slot = items[0].copy();
                slot.setCount(Math.min(remaining, perSlot));
                stock.items.add(slot);
                stock.slots++;
                remaining -= slot.getCount();
            }
            stock.hasSomethingToWithhold = true;
        }

        for (Content content : recipe.getInputContents(GTRecipeCapabilities.FLUID)) {
            if (!(content.content() instanceof SizedFluidIngredient sized)) {
                continue;
            }
            final FluidStack[] fluids = sized.getFluids();
            if (fluids.length == 0 || fluids[0].isEmpty()) {
                stock.problem = "a fluid input matches nothing at all: " + sized.ingredient();
                return stock;
            }
            final FluidStack fluid = fluids[0].copy();
            fluid.setAmount(Math.max(sized.amount(), 1));
            stock.fluids.add(fluid);
            stock.fluidCapacity = Math.max(stock.fluidCapacity, fluid.getAmount());
            stock.hasSomethingToWithhold = true;
        }

        for (Content content : recipe.getOutputContents(GTRecipeCapabilities.ITEM)) {
            if (content.content() instanceof SizedIngredient sized) {
                // Deliberately generous: how much output room a machine really has depends on the
                // machine, so this side is sized to fit rather than to judge. An OUT failure that
                // survives that is structural — a count that overflowed an int, most often — and
                // the reason line prints the entries so it can be read straight off.
                final ItemStack[] produced = sized.getItems();
                final int perSlot = produced.length == 0
                        ? 64
                        : Math.max(produced[0].getMaxStackSize(), 1);
                stock.outputItemSlots += Math.max(1, (sized.count() + perSlot - 1) / perSlot);
                if (stock.outputItemSlots > MAX_OUTPUT_SLOTS) {
                    // Same call as the input side: a greenhouse _max recipe yielding 5,242,880
                    // logs needs more output room than any machine has, and building it here would
                    // cost seconds to prove division. Reported and left unmatched rather than
                    // failed, because "the output bus is too small" is a fact about the harness.
                    stock.tooBig = "produces over " + (MAX_OUTPUT_SLOTS * 64L)
                            + " items in one run, more than any output bus can take - not matched, "
                            + "see the class notes";
                    return stock;
                }
            }
        }
        for (Content content : recipe.getOutputContents(GTRecipeCapabilities.FLUID)) {
            if (content.content() instanceof SizedFluidIngredient sized) {
                stock.outputFluidTanks++;
                stock.fluidCapacity = Math.max(stock.fluidCapacity, Math.max(sized.amount(), 1));
            }
        }
        return stock;
    }

    /**
     * A machine-shaped holder for one match.
     *
     * <p>Built fresh per call because matching consumes from a simulated copy but the handlers
     * themselves are stateful, and because the negative control needs the same shape with nothing
     * in it — {@code stocked} is the only difference between the two.
     */
    private static Holder holder(GTRecipe recipe, Stock stock, boolean stocked) {
        final Holder holder = new Holder();

        final List<IRecipeHandler<?>> inputs = new ArrayList<>();
        final DetachedHandlers.Items items = new DetachedHandlers.Items(Math.max(stock.items.size(), 1), IO.IN);
        if (stocked) {
            for (int slot = 0; slot < stock.items.size(); slot++) {
                items.setStackInSlot(slot, stock.items.get(slot).copy());
            }
        }
        inputs.add(items);

        final DetachedHandlers.Fluids fluids =
                new DetachedHandlers.Fluids(Math.max(stock.fluids.size(), 1), stock.fluidCapacity, IO.IN);
        if (stocked) {
            for (int tank = 0; tank < stock.fluids.size(); tank++) {
                fluids.setFluidInTank(tank, stock.fluids.get(tank).copy());
            }
        }
        inputs.add(fluids);

        final DetachedHandlers.Energy energy =
                new DetachedHandlers.Energy(ENERGY, ENERGY, ENERGY, 0, 0);
        // Withheld along with everything else, so a recipe whose only input is power is still a
        // real test of the negative control.
        energy.setEnergyStored(stocked ? ENERGY : 0);
        inputs.add(energy);
        holder.addHandlerList(RecipeHandlerList.of(IO.IN, inputs));

        final List<IRecipeHandler<?>> outputs = new ArrayList<>();
        outputs.add(new DetachedHandlers.Items(Math.max(stock.outputItemSlots, 1), IO.OUT));
        outputs.add(new DetachedHandlers.Fluids(Math.max(stock.outputFluidTanks, 1), stock.fluidCapacity, IO.OUT));
        if (!recipe.getOutputContents(GTRecipeCapabilities.EU).isEmpty()
                || !recipe.getTickOutputContents(GTRecipeCapabilities.EU).isEmpty()) {
            outputs.add(new DetachedHandlers.Energy(ENERGY, 0, 0, ENERGY, ENERGY));
        }
        holder.addHandlerList(RecipeHandlerList.of(IO.OUT, outputs));

        return holder;
    }

    private static String describe(ActionResult result) {
        final StringBuilder text = new StringBuilder();
        if (result.reason() != null) {
            text.append(result.reason().getString());
        } else {
            text.append("no reason given");
        }
        if (result.capability() != null) {
            text.append(" [").append(result.capability().id)
                    .append(' ').append(result.io()).append(']');
        }
        return text.toString();
    }

    /**
     * The contents GregTech blamed, spelled out.
     *
     * <p>A bare "Insufficient Outputs: Item" says which side failed and nothing about why. Printing
     * the entries of that one capability turns the log line into the answer — an output count that
     * has overflowed an int, or a stack of half a million, reads immediately.
     */
    private static String contents(GTRecipe recipe, ActionResult result) {
        final RecipeCapability<?> capability = result.capability();
        if (capability == null || result.io() == null) {
            return "";
        }
        final List<Content> contents = new ArrayList<>();
        if (result.io() == IO.OUT) {
            contents.addAll(recipe.getOutputContents(capability));
            contents.addAll(recipe.getTickOutputContents(capability));
        } else {
            contents.addAll(recipe.getInputContents(capability));
            contents.addAll(recipe.getTickInputContents(capability));
        }
        if (contents.isEmpty()) {
            return "";
        }
        final StringBuilder text = new StringBuilder(" -> ");
        for (int i = 0; i < contents.size() && i < 8; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(describeContent(contents.get(i)));
        }
        if (contents.size() > 8) {
            text.append(", ...(").append(contents.size()).append(" entries)");
        }
        return text.toString();
    }

    private static String describeContent(Content content) {
        final Object value = content.content();
        if (value instanceof SizedIngredient sized) {
            final ItemStack[] items = sized.getItems();
            final String name = items.length == 0 ? String.valueOf(sized.ingredient())
                    : items[0].getHoverName().getString();
            return sized.count() + "x " + name;
        }
        if (value instanceof SizedFluidIngredient sized) {
            final FluidStack[] fluids = sized.getFluids();
            final String name = fluids.length == 0 ? String.valueOf(sized.ingredient())
                    : fluids[0].getHoverName().getString();
            return sized.amount() + "mB " + name;
        }
        return String.valueOf(value);
    }

    /** The two maps {@code IRecipeCapabilityHolder} needs; {@code addHandlerList} fills them both. */
    private static final class Holder implements com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder {

        private final Map<IO, List<RecipeHandlerList>> proxy = new EnumMap<>(IO.class);
        private final Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> flat = new EnumMap<>(IO.class);

        @Override
        public Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
            return proxy;
        }

        @Override
        public Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
            return flat;
        }
    }

    private enum Kind { MATCHED, FAILED, SKIPPED }

    private record Result(Kind kind, String reason, boolean customIngredient, List<String> warnings) {

        static Result matched(boolean custom, List<String> warnings) {
            return new Result(Kind.MATCHED, null, custom, warnings);
        }

        static Result failed(String reason, List<String> warnings) {
            return new Result(Kind.FAILED, reason, false, warnings);
        }

        static Result skipped(List<String> warnings) {
            return new Result(Kind.SKIPPED, null, false, warnings);
        }
    }

    private static final class Totals {
        private int seen;
        private int matched;
        private int failed;
        private int skipped;
        private int custom;
        private int negativeTested;
        private int refusedEmpty;
    }
}
