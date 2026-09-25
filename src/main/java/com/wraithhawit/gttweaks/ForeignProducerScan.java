package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeCapabilities;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code /gttweaks recipematch skips}: every recipe outside GregTech that makes a GregTech material
 * out of something else, next to the cheapest GregTech recipe that does the same.
 *
 * <p>Issue #1's metal sweep read {@code getResultItem}, which is empty for most modded machine
 * recipes and cannot see a fluid or a Mekanism chemical at all, so it was blind to a chemical
 * route. This reads every non-GregTech recipe through its own codec instead, as JSON, and treats
 * any id under a key naming a result, output or product as something the recipe makes. That is a
 * heuristic over formats this code does not know; the row carries the JSON path so a reader can
 * tell an output from a misread.
 *
 * <p>An id becomes a material through GregTech's own lookups first
 * ({@link ChemicalHelper#getMaterialEntry}, {@link ChemicalHelper#getMaterial(Fluid)}), then through
 * a {@code c:} tag ({@code c:dusts/soldering_alloy}, {@code c:sulfuric_acid}), then by its path
 * alone ({@code mekanism:sulfuric_acid} as a chemical). The last is marked {@code name} and is a
 * lead, not a finding. A recipe that also takes the same material is a conversion — melting,
 * compressing, smelting a dust — and is left out on both sides; so is a GregTech recipe that
 * consumes its own product.
 *
 * <p>For each hit, {@code feedsGT} counts the GregTech recipes whose inputs accept the produced item
 * or fluid. Zero means the product is a look-alike GregTech will not take, which is not a skip.
 *
 * <p>Rows go to {@code gttweaks-skips.tsv} in the server directory, highest GregTech tier first;
 * the command itself prints counts only.
 */
final class ForeignProducerScan {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern OUTPUT_KEY = Pattern.compile("result|output|product|secondar|byproduct",
            Pattern.CASE_INSENSITIVE);

    private final MinecraftServer server;
    private final Map<String, Material> byName = new HashMap<>();
    private final Map<Item, Integer> itemFeeds = new HashMap<>();
    private final Map<Fluid, Integer> fluidFeeds = new HashMap<>();
    private final List<GTRecipe> gtRecipes = new ArrayList<>();

    private ForeignProducerScan(MinecraftServer server) {
        this.server = server;
        GTRegistries.MATERIALS.stream().forEach(material -> byName.putIfAbsent(material.getName(), material));
    }

    /** What an id resolved to, and how sure the resolution is. */
    private record Resolved(Material material, String how, Item item, Fluid fluid) {}

    /**
     * How GregTech makes a material from something else: the lowest voltage over all its recipes,
     * and per machine type the lowest voltage and one recipe at it.
     *
     * <p>Per type, because the lowest voltage alone misleads: ATM's mega chemical reactor runs
     * sulfuric acid at 7 EU/t, which says nothing about what the machine costs to build.
     */
    private static final class GTCost {
        long voltage = Long.MAX_VALUE;
        final Map<String, Long> byType = new HashMap<>();
        final Map<String, String> example = new HashMap<>();

        void add(String type, long recipeVoltage, String recipe) {
            voltage = Math.min(voltage, recipeVoltage);
            final Long current = byType.get(type);
            if (current == null || recipeVoltage < current) {
                byType.put(type, recipeVoltage);
                example.put(type, recipe);
            }
        }

        /** {@code chemical_reactor 30 LV, mega_chemical_reactor 7 ULV}, cheapest first, at most five. */
        String routes() {
            final List<Map.Entry<String, Long>> entries = new ArrayList<>(byType.entrySet());
            entries.sort(Map.Entry.comparingByValue());
            final List<String> parts = new ArrayList<>();
            for (int i = 0; i < entries.size() && i < 5; i++) {
                final Map.Entry<String, Long> entry = entries.get(i);
                parts.add(entry.getKey() + " " + entry.getValue() + " " + tierName(entry.getValue()));
            }
            if (entries.size() > 5) {
                parts.add("+" + (entries.size() - 5) + " more");
            }
            return String.join(", ", parts);
        }
    }

    private final Map<Material, Set<Material>> components = new HashMap<>();

    private record Hit(Material material, String type, String recipe, String id, String path, String how,
            Item item, Fluid fluid) {}

    static List<String> run(MinecraftServer server) {
        return new ForeignProducerScan(server).scan();
    }

    private List<String> scan() {
        final Map<Material, GTCost> gtCost = new HashMap<>();
        final List<Hit> hits = new ArrayList<>();
        final List<String> smelts = new ArrayList<>();
        smelts.add(String.join("\t", "blastK", "material", "type", "recipe", "dustInput", "ingotOutput"));
        final RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        int foreign = 0;
        int unreadable = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            final Recipe<?> recipe = holder.value();
            if (recipe instanceof GTRecipe gt) {
                gtRecipes.add(gt);
                costGT(holder.id().toString(), gt, gtCost);
                continue;
            }
            foreign++;
            final JsonElement json;
            try {
                json = Recipe.CODEC.encodeStart(ops, recipe).result().orElse(null);
            } catch (Throwable t) {
                unreadable++;
                continue;
            }
            if (json == null) {
                unreadable++;
                continue;
            }
            final List<String[]> outputs = new ArrayList<>();
            final List<String> inputs = new ArrayList<>();
            walk(json, "", false, outputs, inputs);
            final Set<Material> consumed = new HashSet<>();
            for (String id : inputs) {
                final Resolved r = resolve(id);
                if (r != null) {
                    consumed.add(r.material());
                }
            }
            final String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            final Set<Material> seen = new HashSet<>();
            for (String[] out : outputs) {
                final Resolved r = resolve(out[0]);
                if (r == null || consumed.contains(r.material()) || !seen.add(r.material())) {
                    continue;
                }
                hits.add(new Hit(r.material(), type, holder.id().toString(), out[0], out[1], r.how(), r.item(),
                        r.fluid()));
            }
            smeltSkips(type, holder.id().toString(), inputs, outputs, smelts);
        }

        hits.sort((a, b) -> {
            final long va = gtCost.containsKey(a.material()) ? gtCost.get(a.material()).voltage : -1;
            final long vb = gtCost.containsKey(b.material()) ? gtCost.get(b.material()).voltage : -1;
            if (va != vb) {
                return Long.compare(vb, va);
            }
            final int byMaterial = a.material().getName().compareTo(b.material().getName());
            return byMaterial != 0 ? byMaterial : a.recipe().compareTo(b.recipe());
        });

        final List<String> rows = new ArrayList<>();
        rows.add(String.join("\t", "gtTier", "gtEUt", "material", "element", "gtRoutes", "type", "recipe",
                "producedId", "jsonPath", "match", "feedsGT"));
        final Map<String, Integer> perTier = new HashMap<>();
        for (Hit hit : hits) {
            final GTCost cost = gtCost.get(hit.material());
            final String tier = cost == null ? "none" : tierName(cost.voltage);
            perTier.merge(tier, 1, Integer::sum);
            rows.add(String.join("\t", tier, cost == null ? "-" : String.valueOf(cost.voltage),
                    hit.material().getName(), String.valueOf(hit.material().isElement()),
                    cost == null ? "-" : cost.routes(), hit.type(), hit.recipe(), hit.id(), hit.path(), hit.how(),
                    String.valueOf(feeds(hit))));
        }

        final Path file = server.getServerDirectory().resolve("gttweaks-skips.tsv");
        final List<String> summary = new ArrayList<>();
        try {
            Files.write(file, rows, StandardCharsets.UTF_8);
            summary.add("wrote " + hits.size() + " rows to " + file.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[gttweaks] could not write {}", file, e);
            summary.add("could not write " + file + ": " + e);
        }
        final Path smeltFile = server.getServerDirectory().resolve("gttweaks-ebf-skips.tsv");
        try {
            Files.write(smeltFile, smelts, StandardCharsets.UTF_8);
            summary.add("wrote " + (smelts.size() - 1) + " dust-to-ingot rows for blast-furnace materials to "
                    + smeltFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[gttweaks] could not write {}", smeltFile, e);
            summary.add("could not write " + smeltFile + ": " + e);
        }
        summary.add(foreign + " non-GregTech recipes read, " + unreadable + " not encodable; "
                + gtRecipes.size() + " GregTech recipes costed, " + gtCost.size() + " materials made by GregTech");
        for (String tier : GTValues.VN) {
            if (perTier.containsKey(tier)) {
                summary.add(tier + ": " + perTier.get(tier) + " foreign producer(s)");
            }
        }
        if (perTier.containsKey("none")) {
            summary.add("no GregTech recipe: " + perTier.get("none") + " foreign producer(s)");
        }
        return summary;
    }

    /**
     * Adds a row when a non-GregTech recipe turns GregTech's own dust of a blast-furnace material
     * into that material's ingot: the shape of every metal skip issue #1 has fixed (MI's titanium,
     * Neo Vitae's and IE's tungsten). The main scan cannot see these, since a recipe that takes the
     * material it makes is a conversion there; this is the one conversion that bypasses a gate.
     *
     * <p>Every blast-gated material is checked, not only those with a raw ore: the ore criterion is
     * what let silicon through the metal sweep. The dust input must accept GregTech's dust item,
     * which a tag ingredient usually does and an item ingredient only when it names that item.
     */
    private void smeltSkips(String type, String recipe, List<String> inputs, List<String[]> outputs,
            List<String> rows) {
        final Map<Material, String> ingots = new HashMap<>();
        for (String[] out : outputs) {
            final Material material = formMaterial(out[0], "ingots/");
            if (material != null && material.getBlastTemperature() > 0) {
                ingots.putIfAbsent(material, out[0]);
            }
        }
        if (ingots.isEmpty()) {
            return;
        }
        for (String input : inputs) {
            final Material material = formMaterial(input, "dusts/");
            if (material == null || !ingots.containsKey(material) || !acceptsGTDust(input, material)) {
                continue;
            }
            rows.add(String.join("\t", String.valueOf(material.getBlastTemperature()), material.getName(), type,
                    recipe, input, ingots.remove(material)));
        }
    }

    /**
     * The material of an item or tag id in one form, {@code dusts/} or {@code ingots/}: a tag
     * {@code c:dusts/silicon} directly, an item through its {@code c:} tags. Null otherwise.
     */
    private Material formMaterial(String raw, String form) {
        final String id = raw.startsWith("#") ? raw.substring(1) : raw;
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        final Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
                if ("c".equals(tag.location().getNamespace()) && tag.location().getPath().startsWith(form)) {
                    final Material material = fromTagPath(tag.location());
                    if (material != null) {
                        return material;
                    }
                }
            }
            return null;
        }
        return "c".equals(location.getNamespace()) && location.getPath().startsWith(form)
                ? fromTagPath(location) : null;
    }

    private static boolean acceptsGTDust(String raw, Material material) {
        final ItemStack dust = ChemicalHelper.get(TagPrefix.dust, material, 1);
        if (dust.isEmpty()) {
            return false;
        }
        final String id = raw.startsWith("#") ? raw.substring(1) : raw;
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return false;
        }
        final Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return dust.is(item);
        }
        return dust.is(TagKey.create(Registries.ITEM, location));
    }

    /**
     * Records {@code gt} as a way to make each material it outputs and does not also take, keeping
     * the lowest voltage per material. A recipe with no voltage (steam, primitive, coke oven) costs 0.
     */
    private void costGT(String id, GTRecipe gt, Map<Material, GTCost> gtCost) {
        final Set<Material> consumed = new HashSet<>();
        for (Content content : gt.getInputContents(GTRecipeCapabilities.ITEM)) {
            if (content.content() instanceof SizedIngredient sized) {
                for (ItemStack stack : sized.getItems()) {
                    addIfResolved(consumed, resolveItem(stack.getItem()));
                }
            }
        }
        for (Content content : gt.getInputContents(GTRecipeCapabilities.FLUID)) {
            if (content.content() instanceof SizedFluidIngredient fluid) {
                for (FluidStack stack : fluid.getFluids()) {
                    addIfResolved(consumed, resolveFluid(stack.getFluid()));
                }
            }
        }
        final long voltage = voltage(gt);
        final String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(gt.getType()));
        final Set<Material> made = new HashSet<>();
        for (Content content : gt.getOutputContents(GTRecipeCapabilities.ITEM)) {
            if (content.content() instanceof SizedIngredient sized) {
                for (ItemStack stack : sized.getItems()) {
                    addIfResolved(made, resolveItem(stack.getItem()));
                }
            }
        }
        for (Content content : gt.getOutputContents(GTRecipeCapabilities.FLUID)) {
            if (content.content() instanceof SizedFluidIngredient fluid) {
                for (FluidStack stack : fluid.getFluids()) {
                    addIfResolved(made, resolveFluid(stack.getFluid()));
                }
            }
        }
        for (Material material : made) {
            if (consumed.contains(material) || recovers(consumed, material)) {
                continue;
            }
            gtCost.computeIfAbsent(material, m -> new GTCost()).add(type, voltage, id);
        }
    }

    /**
     * Whether an input is a metal that already contains {@code material}: centrifuging
     * niobium-titanium dust, arc furnacing a cable. Those recycle the material rather than make it,
     * and counting them put titanium at LV when GregTech gates it behind the HV blast furnace.
     *
     * <p>Only metals, because a non-metal input containing the product is how chemistry works:
     * electrolysing water is GregTech's hydrogen route, and excluding it left oxygen made only by
     * fusion.
     */
    private boolean recovers(Set<Material> consumed, Material material) {
        for (Material input : consumed) {
            if (input.hasProperty(PropertyKey.INGOT) && contains(input).contains(material)) {
                return true;
            }
        }
        return false;
    }

    /** Every material {@code material} is made of, at any depth. */
    private Set<Material> contains(Material material) {
        final Set<Material> known = components.get(material);
        if (known != null) {
            return known;
        }
        final Set<Material> found = new HashSet<>();
        components.put(material, found);
        for (var stack : material.getMaterialComponents()) {
            if (usable(stack.material()) && found.add(stack.material())) {
                found.addAll(contains(stack.material()));
            }
        }
        return found;
    }

    private static void addIfResolved(Set<Material> into, Resolved resolved) {
        if (resolved != null) {
            into.add(resolved.material());
        }
    }

    private static long voltage(GTRecipe gt) {
        for (Content content : gt.getTickInputContents(GTRecipeCapabilities.EU)) {
            if (content.content() instanceof EnergyStack energy && !energy.isEmpty()) {
                return energy.voltage();
            }
        }
        return 0;
    }

    private static String tierName(long voltage) {
        if (voltage <= 0) {
            return "unpowered";
        }
        final byte tier = GTUtil.getTierByVoltage(voltage);
        return tier >= 0 && tier < GTValues.VN.length ? GTValues.VN[tier] : "?";
    }

    /**
     * Collects every string that could be an id, split by whether a key on its path names an output.
     * Each output carries its JSON path so the report shows why it was read as one.
     */
    private static void walk(JsonElement element, String path, boolean output, List<String[]> outputs,
            List<String> inputs) {
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                final boolean isOutput = output || OUTPUT_KEY.matcher(entry.getKey()).find();
                walk(entry.getValue(), path + "." + entry.getKey(), isOutput, outputs, inputs);
            }
        } else if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                walk(array.get(i), path + "[" + i + "]", output, outputs, inputs);
            }
        } else if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            final String value = primitive.getAsString();
            if (value.indexOf(':') > 0 && value.indexOf(' ') < 0) {
                if (output) {
                    outputs.add(new String[] { value, path });
                } else {
                    inputs.add(value);
                }
            }
        }
    }

    /**
     * An id, or a {@code #}-less tag id, as a material: an item first, then a fluid, then a tag
     * name, then the bare path. Returns null for anything that is not a GregTech material.
     */
    private Resolved resolve(String raw) {
        final String id = raw.startsWith("#") ? raw.substring(1) : raw;
        final ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        final Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return resolveItem(item);
        }
        final Fluid fluid = BuiltInRegistries.FLUID.getOptional(location).orElse(null);
        if (fluid != null && fluid != Fluids.EMPTY) {
            return resolveFluid(fluid);
        }
        final Material tagged = fromTagPath(location);
        if (tagged != null) {
            return new Resolved(tagged, "tag", null, null);
        }
        final Material named = byName.get(location.getPath());
        return named == null ? null : new Resolved(named, "name", null, null);
    }

    private Resolved resolveItem(Item item) {
        final MaterialEntry entry = ChemicalHelper.getMaterialEntry(item);
        if (entry != null && !entry.isEmpty() && usable(entry.material())) {
            return new Resolved(entry.material(), "gt", item, null);
        }
        for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
            final Material material = fromTagPath(tag.location());
            if (material != null) {
                return new Resolved(material, "tag", item, null);
            }
        }
        return null;
    }

    private Resolved resolveFluid(Fluid fluid) {
        final Material material = ChemicalHelper.getMaterial(fluid);
        if (usable(material)) {
            return new Resolved(material, "gt", null, fluid);
        }
        for (TagKey<Fluid> tag : fluid.builtInRegistryHolder().tags().toList()) {
            final Material tagged = fromTagPath(tag.location());
            if (tagged != null) {
                return new Resolved(tagged, "tag", null, fluid);
            }
        }
        return null;
    }

    /** {@code c:dusts/soldering_alloy} or {@code c:sulfuric_acid} as a material; nothing else. */
    private Material fromTagPath(ResourceLocation tag) {
        if (!"c".equals(tag.getNamespace())) {
            return null;
        }
        final String path = tag.getPath();
        final int slash = path.lastIndexOf('/');
        final String name = slash < 0 ? path : path.substring(slash + 1);
        final Material material = byName.get(name.toLowerCase(Locale.ROOT));
        return usable(material) ? material : null;
    }

    private static boolean usable(Material material) {
        return material != null && !material.isNull();
    }

    /** How many GregTech recipes take the produced item or fluid as an input, cached per thing. */
    private int feeds(Hit hit) {
        if (hit.item() != null) {
            return itemFeeds.computeIfAbsent(hit.item(), item -> {
                final ItemStack stack = new ItemStack(item);
                int count = 0;
                for (GTRecipe gt : gtRecipes) {
                    for (Content content : gt.getInputContents(GTRecipeCapabilities.ITEM)) {
                        if (content.content() instanceof SizedIngredient sized && sized.ingredient().test(stack)) {
                            count++;
                            break;
                        }
                    }
                }
                return count;
            });
        }
        if (hit.fluid() != null) {
            return fluidFeeds.computeIfAbsent(hit.fluid(), fluid -> {
                final FluidStack stack = new FluidStack(fluid, 1000);
                int count = 0;
                for (GTRecipe gt : gtRecipes) {
                    for (Content content : gt.getInputContents(GTRecipeCapabilities.FLUID)) {
                        if (content.content() instanceof SizedFluidIngredient sized
                                && sized.ingredient().test(stack)) {
                            count++;
                            break;
                        }
                    }
                }
                return count;
            });
        }
        return -1;
    }
}
