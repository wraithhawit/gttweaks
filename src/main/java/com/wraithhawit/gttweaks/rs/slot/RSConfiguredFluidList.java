package com.wraithhawit.gttweaks.rs.slot;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The tank a Refined Storage fluid hatch presents to GregTech: a fixed run of configured slots.
 *
 * <p>The fluid twin of {@link RSConfiguredItemList}, and a good deal shorter than it, because
 * {@link NotifiableFluidTank} already takes a list of {@link CustomFluidTank}s and delegates to
 * them. Handing it the configured slots directly is enough — there is no equivalent of the item
 * side's separate delegate handler to build, since the slots already <em>are</em> the type the
 * superclass wants.
 *
 * <p>Everything that would put fluid in is refused by the slots themselves, so a pipe pointed at
 * one of these parts cannot fill it; the only legitimate source of stock is the network sync.
 */
public class RSConfiguredFluidList extends NotifiableFluidTank implements RSConfigurableSlotList {

    private final RSConfiguredFluidSlot[] inventory;

    public RSConfiguredFluidList(int slots) {
        this(slots, RSConfiguredFluidSlot::new);
    }

    public RSConfiguredFluidList(int slots, Supplier<RSConfiguredFluidSlot> slotFactory) {
        this(build(slots, slotFactory));
    }

    private RSConfiguredFluidList(List<RSConfiguredFluidSlot> slots) {
        super(List.copyOf(slots), IO.IN, IO.NONE);
        this.inventory = slots.toArray(new RSConfiguredFluidSlot[0]);
    }

    private static List<RSConfiguredFluidSlot> build(int slots,
                                                     Supplier<RSConfiguredFluidSlot> slotFactory) {
        List<RSConfiguredFluidSlot> built = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            built.add(slotFactory.get());
        }
        return built;
    }

    public RSConfiguredFluidSlot[] getInventory() {
        return inventory;
    }

    @Override
    public int getConfigurableSlotCount() {
        return inventory.length;
    }

    @Override
    public RSConfiguredSlot getConfigurableSlot(int index) {
        return inventory[index].slot();
    }

    @Override
    public boolean isStocking() {
        return inventory.length > 0 && inventory[0] instanceof RSStockingFluidSlot;
    }
}
