package com.wraithhawit.gttweaks.rs.machine;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;

import com.wraithhawit.gttweaks.rs.slot.RSConfigurableSlotList;
import com.wraithhawit.gttweaks.rs.slot.RSConfiguredSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * A stocking part, as the other stocking parts on the same multiblock see it.
 *
 * <p>Each stocking slot mirrors the network's whole amount of its resource. Two parts on one
 * multiblock, both configured for the same resource, would show it twice what exists:
 * with 320 on the network, a recipe needing 480 passes the simulated pass by taking 320 from one
 * part and 160 from the other, then comes up short when it runs. The first-slot-per-resource rule in
 * each part's {@code syncNetwork} only covers slots of one part; this covers parts.
 *
 * <p>GregTech closes the same gap for its ME stocking parts with {@code testConfiguredInOtherPart},
 * which refuses the config in the widget, and {@code validateConfig}, which clears clashing configs
 * the tick after a part joins a controller. Both exempt a distinct bus; this does not (see
 * {@link #poolPartners}). The refusal is otherwise ported as it is ({@code RSConfigActions}).
 * The clearing is replaced by {@link #claimMirror}, run on every sync: of the parts configured for a
 * resource, only the one at the lowest position mirrors it, and it empties the others' mirrors when
 * it takes it. That covers everything that can bring two such configs together -- configs saved
 * before this guard existed, a structure formed around parts already configured -- without deleting
 * a config the player set, and without a window in which both parts report it, since whichever of
 * the two syncs first settles it for both.
 */
public interface RSStockingPart {

    /** The machine itself. Not {@code self()}, which {@code MetaMachine} already declares. */
    MultiblockPartMachine part();

    RSConfigurableSlotList stockingSlots();

    default boolean hasConfigured(ResourceKey resource) {
        RSConfigurableSlotList slots = stockingSlots();
        for (int i = 0; i < slots.getConfigurableSlotCount(); i++) {
            ResourceAmount config = slots.getConfigurableSlot(i).getConfig();
            if (config != null && config.resource().equals(resource)) {
                return true;
            }
        }
        return false;
    }

    /** Empties this part's mirror of a resource. Safe: a mirror is a reading, not held material. */
    default void dropMirrorOf(ResourceKey resource) {
        RSConfigurableSlotList slots = stockingSlots();
        for (int i = 0; i < slots.getConfigurableSlotCount(); i++) {
            RSConfiguredSlot slot = slots.getConfigurableSlot(i);
            ResourceAmount stock = slot.getStock();
            if (stock != null && stock.resource().equals(resource)) {
                slot.setStock(null);
            }
        }
    }

    /** Whether another part in this one's pool is configured for the resource. */
    default boolean configuredInOtherPart(ResourceKey resource) {
        for (RSStockingPart other : poolPartners(this)) {
            if (other.hasConfigured(resource)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The other stocking parts on every multiblock this part belongs to. Empty when the part is not
     * formed, since no recipe reads it then.
     *
     * <p>Every one of them, distinct or not. GregTech's guard exempts a distinct bus, and so did the
     * first version of this one, but a distinct bus's stock is still combined with the shared parts'
     * when a recipe is matched: on the test server (2026-09-25) a distinct bus and a plain one each
     * mirroring the network's 3 flint dust let an Implosion Compressor accept a recipe needing 4, and
     * the run took the 3 dust and the TNT and produced nothing. Nor is the pool narrowed to parts on
     * the same network, which would be more permissive: a part that drops off its network keeps its
     * last mirror, and a pool that forgot it would leave that stale reading uncontested.
     */
    static List<RSStockingPart> poolPartners(RSStockingPart part) {
        MultiblockPartMachine self = part.part();
        if (!self.isFormed()) {
            return List.of();
        }
        List<RSStockingPart> partners = new ArrayList<>();
        for (MultiblockControllerMachine controller : self.getControllers()) {
            for (MultiblockPartMachine other : controller.getParts()) {
                if (other != self && other instanceof RSStockingPart stocking
                        && !partners.contains(stocking)) {
                    partners.add(stocking);
                }
            }
        }
        return partners;
    }

    /**
     * Decides whether this part mirrors a resource for its pool, and if it does, empties every
     * partner's mirror of it. The part at the lowest position among those configured for the
     * resource wins, which every part in the pool computes the same way.
     */
    static boolean claimMirror(RSStockingPart part, List<RSStockingPart> partners, ResourceKey resource) {
        List<RSStockingPart> rivals = new ArrayList<>();
        for (RSStockingPart other : partners) {
            if (other.hasConfigured(resource)) {
                if (other.part().getBlockPos().compareTo(part.part().getBlockPos()) < 0) {
                    return false;
                }
                rivals.add(other);
            }
        }
        for (RSStockingPart rival : rivals) {
            rival.dropMirrorOf(resource);
        }
        return true;
    }
}
