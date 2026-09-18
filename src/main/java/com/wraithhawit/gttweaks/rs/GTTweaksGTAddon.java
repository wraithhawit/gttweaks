package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/**
 * Declares this mod to GregTech as an addon, which is what lets it register machines.
 *
 * <p>{@code AddonFinder} finds this class by scanning every loaded mod file for {@link GTAddon}
 * whose value equals the owning mod's id, then instantiates it reflectively. So the annotation value
 * must stay {@code "gttweaks"}, and this class must keep a public no-argument constructor.
 *
 * <p>It answers with the registrate and does nothing else. The parts are not built here: this
 * callback runs at the end of GregTech's init, which is still before {@code GTBlocks.init()}, and
 * building a machine reads GregTech's casings. Both this and the mod constructor were tried and both
 * died the same way — see {@code RSMachines.initOnce()}, which is where the parts are built and why.
 */
@GTAddon("gttweaks")
public class GTTweaksGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTTweaksRegistrate.REGISTRATE;
    }

    @Override
    public void gtInitComplete() {
    }
}
