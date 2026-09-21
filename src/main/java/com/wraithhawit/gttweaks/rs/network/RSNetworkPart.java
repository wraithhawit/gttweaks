package com.wraithhawit.gttweaks.rs.network;

/**
 * A GregTech part that carries a Refined Storage node.
 *
 * <p>Exists so that {@code RSCapabilities} has something to ask other than "are you an item bus".
 * The fluid hatches extend GregTech's fluid part class, not the item bus one, so there is no common
 * superclass to cast to — and a capability registration that assumed one would compile and then
 * throw {@link ClassCastException} the first time a cable touched a hatch.
 */
public interface RSNetworkPart {

    RSNodeHolder getNodeHolder();

    /**
     * What the machine last decided about the network, as opposed to what the node believes right
     * now.
     *
     * <p>Both are worth having separately: the machine's copy is the one that is synced to the
     * client and shown in a GUI, and a disagreement between the two is the shape most of the
     * lifecycle bugs in these parts have taken.
     */
    boolean isOnline();
}
