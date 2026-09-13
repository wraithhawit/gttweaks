package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.wraithhawit.gttweaks.ingredient.GTTweaksIngredients;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.slf4j.Logger;

/**
 * Java-side fixes for running GregTech CEu Modern in All the Mods 10.
 *
 * <p>The work is done by mixins; see {@code JeiCallWrapperMixin} for the load
 * failure and {@code OverlayStackMixin} for ModularUI's early config reads.
 *
 * <p>An earlier version of this class tried to fix the load failure by calling
 * {@code CommonProxy.onRegister(null)} during construction, to attach
 * GregTech's DeferredRegisters before any registry event. That was wrong twice
 * over: GregTech's init legitimately cannot run that early (it reaches JEI), and
 * {@code onRegister} sets its {@code didRunRegistration} flag before doing any
 * work — so a half-completed early call left the flag set and guaranteed
 * GregTech would never retry. It made the very failure it was aimed at
 * unconditional. Do not reintroduce it.
 *
 * <p>The probe below stays. It costs one log line per registry and prints the
 * fact that took far too long to establish: whether {@code gtceu:data_item} is
 * bound as each registry is dispatched. A run where it flips to true before
 * {@code minecraft:item} is a run where the fix held.
 */
@Mod(GTTweaks.MOD_ID)
public class GTTweaks {

    public static final String MOD_ID = "gttweaks";
    private static final Logger LOGGER = LogUtils.getLogger();

    public GTTweaks(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, GTTweaksConfig.CLIENT_SPEC);
        GTTweaksIngredients.REGISTRY.register(modBus);
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, GTTweaks::probeRegistryDispatch);
    }

    private static void probeRegistryDispatch(RegisterEvent event) {
        LOGGER.info("[gttweaks] RegisterEvent {} | gtceu:data_item bound={}",
                event.getRegistryKey().location(), boundState());
    }

    private static String boundState() {
        try {
            return String.valueOf(GTDataComponents.DATA_ITEM.isBound());
        } catch (Throwable t) {
            return "unavailable(" + t.getClass().getSimpleName() + ")";
        }
    }
}
