package com.wraithhawit.gttweaks;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

/**
 * Reports whether named recipe ids made it into the server's recipe manager.
 *
 * <p>For recipes that vanish without a log line. "Unknown recipe" in game only says the id is absent
 * in one pack; running the same ids on the stripped headless server tells whether the recipe file
 * itself loads, which splits "the recipe is broken" from "something in the full pack drops it".
 *
 * <p>Off unless {@code -Dgttweaks.probeRecipes=namespace:path,namespace:path} is set.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.probeRecipes";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event) {
        final String ids = System.getProperty(PROPERTY, "").trim();
        if (ids.isEmpty()) {
            return;
        }
        final MinecraftServer server = event.getServer();
        for (String raw : ids.split(",")) {
            final String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                LOGGER.warn("[gttweaks] recipe probe: '{}' is not a valid id", id);
                continue;
            }
            final boolean present = server.getRecipeManager().byKey(location).isPresent();
            LOGGER.info("[gttweaks] recipe probe: {} {}", id, present ? "PRESENT" : "ABSENT");
        }
    }
}
