package com.wraithhawit.gttweaks.rs.slot;

/**
 * The part of a configured bus or hatch the config widget needs to see.
 *
 * <p>{@link RSConfiguredItemList} extends GregTech's item handler and {@link RSConfiguredFluidList}
 * its fluid tank, so the two have no common supertype of their own. This is the seam that lets one
 * widget drive both, and it is deliberately the smallest one possible: a count, a slot, and whether
 * the amounts on those slots mean anything.
 *
 * <p>This is GregTech's {@code IConfigurableSlotList} in the same role.
 */
public interface RSConfigurableSlotList {

    int getConfigurableSlotCount();

    RSConfiguredSlot getConfigurableSlot(int index);

    /**
     * Whether these slots mirror the network rather than hold anything.
     *
     * <p>A stocking part ignores the configured amount, so the widget must not offer to edit one,
     * and its stock is not material anybody may pick up — doing so would create items out of a
     * reading. Both are refused in the widget and again on the server; see
     * {@code RSConfigActions#register}.
     *
     * <p>Asked of the list rather than of a slot because the fluid side has nowhere to answer it.
     * {@code RSConfiguredFluidSlot} extends {@code CustomFluidTank} and only <em>holds</em> an
     * {@link RSConfiguredSlot}, so a stocking fluid slot's stocking-ness lives on the outer object
     * that the list knows about and the configured slot does not.
     */
    boolean isStocking();
}
