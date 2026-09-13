package com.wraithhawit.gttweaks;

/**
 * Recognises GregTech's AE2 multiblock parts without referencing AE2 or GregTech's AE2 classes.
 *
 * <p>Every ME bus and hatch GregTech 8 ships ({@code MEBusPartMachine} and {@code MEHatchPartMachine}
 * and their input, stocking, output and pattern-buffer subclasses) implements
 * {@code IGridConnectedMachine}. Matching that interface by name keeps callers loadable when AE2 is
 * absent: an {@code instanceof} would put the interface in the caller's constant pool, while a machine
 * whose class really implements it can only exist when AE2 is loaded.
 */
public final class NetworkParts {

    static final String GRID_CONNECTED_MACHINE =
            "com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine";

    private NetworkParts() {
    }

    /** Whether {@code machine} is a GregTech AE2 grid-connected part. */
    public static boolean isGridConnected(Object machine) {
        return machine != null && implementsNamed(machine.getClass(), GRID_CONNECTED_MACHINE);
    }

    /** Whether {@code type}, a superclass, or any interface they extend is named {@code interfaceName}. */
    static boolean implementsNamed(Class<?> type, String interfaceName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(interfaceName)) {
                return true;
            }
            for (Class<?> iface : current.getInterfaces()) {
                if (implementsNamed(iface, interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
