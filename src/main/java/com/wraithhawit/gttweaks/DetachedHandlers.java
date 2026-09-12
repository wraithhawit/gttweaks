package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;

/**
 * GregTech's own recipe handlers, detached from any machine, so a recipe can be matched without a
 * world.
 *
 * <p>These exist because the alternative — writing our own {@code IRecipeHandler} implementations —
 * would be testing our reimplementation of GregTech's matching rules rather than GregTech's. The
 * handlers below are the real classes, running the real {@code handleRecipeInner}; only the three
 * methods that reach for a block entity are replaced.
 *
 * <p>Two facts from the 8.0.0-SNAPSHOT bytecode make this work, and both should be re-checked
 * after a GregTech update:
 *
 * <ul>
 *   <li>{@code NotifiableItemStackHandler(int, IO)}, {@code NotifiableFluidTank(int, int, IO)} and
 *       {@code NotifiableEnergyContainer}'s constructor take <b>no</b> {@code MetaMachine} — GT8
 *       sets it later through {@code MachineTrait.setMachine}. A trait can therefore be built
 *       standalone.
 *   <li>{@code MachineTrait.getMachine()} <b>throws</b> {@code IllegalStateException} when the
 *       machine was never set, rather than returning null. That is what forces the override below.
 *       Returning null is safe because every place the machine reaches from a recipe handler is
 *       null-guarded: {@code NotifiableItemStackHandler.handleRecipe} tests
 *       {@code instanceof MultiblockPartMachine} (false for null) and then {@code ifnull}, and the
 *       fluid tank follows the same shape.
 * </ul>
 *
 * <p>The consequence of a null machine is that a detached handler cannot report a consumed input
 * back to a controller's {@code RecipeLogic} — which is exactly the bookkeeping a no-world check
 * has no use for.
 */
final class DetachedHandlers {

    private DetachedHandlers() {}

    /**
     * An item bus with no machine behind it.
     *
     * <p>{@code onContentsChanged} is replaced rather than suppressed: the original reads
     * {@code getLevel()} and {@code getBlockPos()} to age spoilable items, which cannot work
     * detached. {@code isEmpty} is recomputed each call because the original serves a cache that
     * only {@code onContentsChanged} invalidates, and that method no longer runs.
     */
    static final class Items extends NotifiableItemStackHandler {

        Items(int slots, IO io) {
            super(slots, io);
        }

        @Override
        public MetaMachine getMachine() {
            return null;
        }

        @Override
        public void onContentsChanged() {
            notifyListeners();
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < getSlots(); slot++) {
                if (!getStackInSlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A fluid hatch with no machine behind it. See {@link Items} for why each override is here. */
    static final class Fluids extends NotifiableFluidTank {

        Fluids(int tanks, int capacity, IO io) {
            super(tanks, capacity, io);
        }

        @Override
        public MetaMachine getMachine() {
            return null;
        }

        @Override
        public void onContentsChanged() {
            notifyListeners();
        }

        @Override
        public boolean isEmpty() {
            for (int tank = 0; tank < getTanks(); tank++) {
                if (!getFluidInTank(tank).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * An energy hatch with no machine behind it.
     *
     * <p>{@code checkOutputSubscription} is the only extra override needed: {@code setEnergyStored}
     * calls it on every change and it goes straight to {@code getMachine().subscribeServerTick}.
     * Nothing detached ticks, so there is no subscription to keep.
     */
    static final class Energy extends NotifiableEnergyContainer {

        Energy(long capacity, long inputVoltage, long inputAmperage,
               long outputVoltage, long outputAmperage) {
            super(capacity, inputVoltage, inputAmperage, outputVoltage, outputAmperage);
        }

        @Override
        public MetaMachine getMachine() {
            return null;
        }

        @Override
        public void checkOutputSubscription() {}
    }
}
