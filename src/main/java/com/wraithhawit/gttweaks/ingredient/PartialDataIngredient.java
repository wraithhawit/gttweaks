package com.wraithhawit.gttweaks.ingredient;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.List;
import java.util.stream.Stream;

/**
 * An ingredient that matches an item whose {@code minecraft:custom_data} <em>contains</em> the
 * given tag, rather than equalling it.
 *
 * <p>Nothing in 1.21 can express this. {@link net.neoforged.neoforge.common.crafting.DataComponentIngredient}
 * — and both subclasses shipped in this pack, GregTech's {@code ExDataComponentIngredient} and
 * Productive Bees' {@code ComponentIngredient} — test each expected component for
 * <em>equality</em>, and {@code CustomData}'s equality is equality of the whole tag. That is
 * fine for a one-value component like {@code productivebees:bee_type}, and useless for anything
 * that carries a payload.
 *
 * <p>It exists for the ported Apiary recipes. A Productive Bees bee cage keeps the caged bee's
 * entire entity save under {@code minecraft:custom_data} — health, UUID, age, genes — with the
 * bee's identity in two of those keys ({@code entity}, and {@code type} when the entity is
 * {@code productivebees:configurable_bee}). A recipe has to key on those two keys and ignore the
 * rest, which is what Forge's {@code PartialNBTIngredient} did on 1.20 and what KubeJS exposed as
 * {@code IngredientHelper.weakNBT}. KubeJS 2101 deleted that binding along with the platform
 * helper behind it.
 *
 * <p>Scripts build it without any KubeJS plugin, since Rhino can call a static on a loaded class:
 *
 * <pre>{@code
 * const $Partial = Java.loadClass('com.wraithhawit.gttweaks.ingredient.PartialDataIngredient')
 * $Partial.ofItem('productivebees:bee_cage', '{entity:"productivebees:configurable_bee",type:"productivebees:experience"}')
 * }</pre>
 *
 * <p>{@link #isSimple()} is false, so the network path uses {@link IngredientType#streamCodec()}
 * and the client tests stacks the same way the server does.
 */
public record PartialDataIngredient(HolderSet<Item> items, CompoundTag data) implements ICustomIngredient {

    public static final MapCodec<PartialDataIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(PartialDataIngredient::items),
            CompoundTag.CODEC.fieldOf("data").forGetter(PartialDataIngredient::data))
            .apply(instance, PartialDataIngredient::new));

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty() || !this.items.contains(stack.getItemHolder())) {
            return false;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            // An empty requirement means "any stack of these items", including one with no data.
            return this.data.isEmpty();
        }
        // compareNbt(expected, actual, true): every key in expected must be present and equal in
        // actual; extra keys in actual are ignored. The third argument makes list order matter,
        // which matches what the 1.20 partial-NBT ingredient did.
        return NbtUtils.compareNbt(this.data, custom.getUnsafe(), true);
    }

    /**
     * One display stack per accepted item, carrying the required data so recipe viewers show a
     * cage that names the right bee rather than an empty one. At least one stack must come back
     * or the ingredient counts as accidentally empty and the recipe is dropped.
     */
    @Override
    public Stream<ItemStack> getItems() {
        return this.items.stream().map(holder -> {
            ItemStack stack = new ItemStack(holder);
            if (!this.data.isEmpty()) {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(this.data));
            }
            return stack;
        });
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return GTTweaksIngredients.PARTIAL_DATA.get();
    }

    /**
     * Script-facing factory, named ofItem rather than of because Rhino cannot choose between two
     * two-argument overloads when both are reachable from (string, string) - it reports the call as
     * ambiguous rather than picking the String one. {@code snbt} is SNBT, the same text the 1.20
     * scripts passed to
     * {@code Item.of(id, nbt)} — so a ported line keeps its original NBT string verbatim.
     *
     * @throws IllegalArgumentException if the item id is unknown or the SNBT will not parse,
     *         rather than silently matching nothing. A recipe with a silently empty ingredient
     *         looks complete in EMI and can never be crafted, which is the failure mode this
     *         whole port keeps hitting.
     */
    public static Ingredient ofItem(String itemId, String snbt) {
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "gttweaks partial_data: unknown item '" + itemId + "'"));
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(snbt);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "gttweaks partial_data: unparseable SNBT for '" + itemId + "': " + snbt, e);
        }
        return of(HolderSet.direct(List.of(item.builtInRegistryHolder())), tag);
    }

    public static Ingredient of(HolderSet<Item> items, CompoundTag data) {
        return new PartialDataIngredient(items, data).toVanilla();
    }

    // ICustomIngredient requires equals/hashCode; the record gives both. CompoundTag's are
    // value-based, HolderSet's are not, so two ingredients built from the same arguments compare
    // equal only when they share a holder set. That is good enough here: GregTech's recipe
    // lookup hashes the recipe id, not its ingredients.
}
