package com.wraithhawit.gttweaks;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

/**
 * Stops the server once the verifiers have run, so a headless check is a command that finishes
 * rather than a server someone has to remember to shut down.
 *
 * <p>{@link EventPriority#LOWEST} is load-bearing: every other {@code ServerStartedEvent} listener,
 * including both verifiers, must have run before the halt. {@code halt(false)} rather than
 * {@code System.exit} so the world is saved and the log is flushed — a truncated log would defeat
 * the point of a check that is read by grepping.
 *
 * <p>Off unless {@code -Dgttweaks.exitAfterCheck=true} is set.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class HeadlessCheckExit {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.exitAfterCheck";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        LOGGER.info("[gttweaks] checks complete, halting server");
        event.getServer().halt(false);
    }
}
