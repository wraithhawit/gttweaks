package com.wraithhawit.gttweaks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** NetworkParts.implementsNamed over the shapes GregTech's ME parts use: direct, inherited, and via a sub-interface. */
class NetworkPartsTest {

    interface GridConnected {}

    interface StockingPart extends GridConnected {}

    static class PlainBus {}

    static class MeBus extends PlainBus implements GridConnected {}

    static class MeStockingBus extends MeBus {}

    static class InterfaceOnlyPart implements StockingPart {}

    private static final String NAME = GridConnected.class.getName();

    @Test
    void recognisesDirectInheritedAndSubInterfaceImplementations() {
        assertTrue(NetworkParts.implementsNamed(MeBus.class, NAME));
        assertTrue(NetworkParts.implementsNamed(MeStockingBus.class, NAME));
        assertTrue(NetworkParts.implementsNamed(InterfaceOnlyPart.class, NAME));
    }

    @Test
    void plainPartsAndNullAreNotNetworkBacked() {
        assertFalse(NetworkParts.implementsNamed(PlainBus.class, NAME));
        assertFalse(NetworkParts.implementsNamed(Object.class, NAME));
        assertFalse(NetworkParts.isGridConnected(null));
        assertFalse(NetworkParts.isGridConnected(new MeBus()), "only GregTech's own interface name counts");
    }
}
