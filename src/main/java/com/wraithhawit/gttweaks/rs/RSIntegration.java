package com.wraithhawit.gttweaks.rs;

import net.neoforged.fml.ModList;

/**
 * Whether the Refined Storage parts can exist at all.
 *
 * <p>Every class under this package touches either GregTech's machine registry or Refined Storage's
 * network API, so nothing here may be class-loaded before {@link #available()} has said yes.
 * GregTech is a required dependency of this mod; Refined Storage is not.
 */
public final class RSIntegration {

    public static final String RS_MOD_ID = "refinedstorage";

    private static Boolean available;

    private RSIntegration() {
    }

    public static boolean available() {
        if (available == null) {
            available = ModList.get().isLoaded(RS_MOD_ID);
        }
        return available;
    }
}
