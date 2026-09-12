package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ItemLike;

import org.spongepowered.asm.mixin.Mixin;

/**
 * GregTech's material lookup is not thread-safe, and EMI calls it from several
 * threads at once.
 *
 * <p>{@code ChemicalHelper.getMaterialEntry(ItemLike)} lazily fills a plain
 * {@code Object2ObjectOpenHashMap} — first a bulk {@code put} loop draining
 * ITEM_MATERIAL_ENTRY, then a {@code computeIfAbsent} — with no synchronisation
 * anywhere. EMI bakes its search index on background threads, and because
 * {@code search-tooltip-by-default} is on it renders every item's tooltip to do
 * so; GregTech's tooltip handler calls straight into this method. Two threads
 * resizing the same fastutil map corrupts it mid-rehash, which surfaces as:
 *
 * <pre>
 *   ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 65537
 *     at Object2ObjectOpenHashMap.rehash
 *     at ChemicalHelper.getMaterialEntry(ChemicalHelper.java:200)
 *     at TooltipsHandler.appendTooltips
 * </pre>
 *
 * Once corrupted the map stays broken, so every subsequent item fails the same
 * way — 16,384 of them in one run — and EMI's index never finishes building.
 * Nothing crashes, EMI just silently never loads.
 *
 * <p>Wrapping the whole method is deliberate. Guarding only the
 * {@code computeIfAbsent} would leave the {@code put} loop above it unprotected,
 * and reimplementing the body here would duplicate logic that upstream will
 * change. Serialising entry means the lazy fill happens exactly once no matter
 * how many threads arrive; the contention cost is paid on cache misses, and
 * after the first pass callers hit the fast path.
 */
@Mixin(value = com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper.class, remap = false)
public class ChemicalHelperMixin {

    @WrapMethod(method = "getMaterialEntry(Lnet/minecraft/world/level/ItemLike;)Lcom/gregtechceu/gtceu/api/data/chemical/material/stack/MaterialEntry;")
    private static MaterialEntry gttweaks$serialiseMaterialLookup(ItemLike itemLike,
                                                                  Operation<MaterialEntry> original) {
        synchronized (ChemicalHelperMixin.class) {
            return original.call(itemLike);
        }
    }
}
