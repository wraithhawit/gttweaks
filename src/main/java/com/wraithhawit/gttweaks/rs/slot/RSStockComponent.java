package com.wraithhawit.gttweaks.rs.slot;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import com.wraithhawit.gttweaks.GTTweaks;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a broken input part was still holding, carried on the machine item until it is placed again.
 *
 * <p>An input bus or hatch hands its stock back to the network when it is broken, and that is the
 * right answer whenever the network will take it. When the network is full or the part was not
 * connected, it will not, and before this the stock was simply lost with the block. Dropping it into
 * the world is not the fix: a slot's amount is a {@code long} and a part can legitimately hold
 * hundreds of thousands of items, which as entities would be a far worse problem than the loss.
 * So it rides on the item instead, and goes back into the slots when the part is placed.
 *
 * <p>Only the stock travels, not the configuration. That is deliberate and it is what makes the
 * restored stock harmless: a placed part with stock it is not configured for sees it as excess and
 * pushes it into the network on its first sync, which is exactly where the player wanted it. If the
 * new network is full too, the part keeps holding it and tries again, as it did before.
 *
 * <p>One slot per list entry, positions preserved, so this stays meaningful if a later version
 * decides to carry the configuration as well.
 */
public final class RSStockComponent {

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GTTweaks.MOD_ID);

    /**
     * Refined Storage's own optional-resource codecs, so a held item keeps its data components on
     * the machine item exactly as it keeps them in the part's saved data and over the wire.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Optional<ResourceAmount>>> STREAM_CODEC =
            ResourceCodecs.AMOUNT_STREAM_OPTIONAL_CODEC.apply(ByteBufCodecs.list());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Optional<ResourceAmount>>>> HELD_STOCK =
            REGISTRY.register("rs_held_stock", () -> DataComponentType
                    .<List<Optional<ResourceAmount>>>builder()
                    .persistent(ResourceCodecs.AMOUNT_OPTIONAL_CODEC.listOf())
                    .networkSynchronized(STREAM_CODEC)
                    .build());

    private RSStockComponent() {
    }

    /**
     * Puts whatever the slots are still holding onto the machine item being dropped.
     *
     * <p>Call this <em>after</em> the part has tried to hand its stock back, or the network gets the
     * items and the item carries a copy of them. GregTech's drop path makes that ordering available:
     * {@code MetaMachineBlock.getDrops} calls {@code modifyDrops} on the machine and only then
     * {@code saveToItem}, which is what reaches this.
     *
     * <p>A stocking part is skipped entirely, and that is the whole reason this asks. Its stock is a
     * reading of what the network holds rather than material it carries, so writing it onto the item
     * would hand the player a second copy of items the network still has.
     */
    public static void collectInto(DataComponentMap.Builder builder, RSConfigurableSlotList slotList) {
        if (slotList.isStocking()) {
            return;
        }
        List<Optional<ResourceAmount>> held = new ArrayList<>(slotList.getConfigurableSlotCount());
        boolean anything = false;
        for (int i = 0; i < slotList.getConfigurableSlotCount(); i++) {
            ResourceAmount stock = slotList.getConfigurableSlot(i).getStock();
            held.add(Optional.ofNullable(stock));
            anything |= stock != null;
        }
        if (anything) {
            builder.set(HELD_STOCK.get(), List.copyOf(held));
        }
    }

    /**
     * Puts a carried stock back into the slots of a part that has just been placed.
     *
     * <p>Positions are honoured where the list is long enough and anything past its end is dropped,
     * so a saved stock cannot make a part report more slots than it has. Nothing else is needed to
     * get the items home: the part is not configured for them, so its first sync sees them as excess
     * and pushes them into the network.
     *
     * <p>Takes the component's value rather than the {@code DataComponentInput} it came out of,
     * because that type is protected on {@link BlockEntity} and only a machine can name it.
     */
    public static void applyFrom(@Nullable List<Optional<ResourceAmount>> held,
                                 RSConfigurableSlotList slotList) {
        if (held == null || slotList.isStocking()) {
            return;
        }
        int slots = Math.min(held.size(), slotList.getConfigurableSlotCount());
        for (int i = 0; i < slots; i++) {
            slotList.getConfigurableSlot(i).setStock(held.get(i).orElse(null));
        }
    }
}
