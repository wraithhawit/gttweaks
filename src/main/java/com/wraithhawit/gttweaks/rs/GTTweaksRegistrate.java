package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.wraithhawit.gttweaks.GTTweaks;

/**
 * This mod's handle on GregTech's registration machinery.
 *
 * <p>Created with {@code registerEvents = false} and wired to the mod bus by hand from
 * {@link GTTweaks}, exactly as GregTech does for its own {@code GTRegistration.REGISTRATE}. The
 * convenience form, {@code GTRegistrate.create(modId)}, looks the event bus up through
 * {@code ModList.get().getModContainerById(modId)} — and this registrate is first touched from
 * {@link GTTweaksGTAddon}, which GregTech's {@code AddonFinder} instantiates during <em>its</em>
 * construction, before ours. Whether our container is answerable that early is not something to
 * depend on: when the lookup misses, GTRegistrate only logs and returns, and the result is a mod
 * that registers nothing with no exception to explain it.
 *
 * <p>Holding it here, apart from {@link RSMachines}, keeps it loadable without Refined Storage. The
 * addon class must answer {@code getRegistrate()} whether or not the parts exist.
 */
public final class GTTweaksRegistrate {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(GTTweaks.MOD_ID, false);

    private GTTweaksRegistrate() {
    }
}
