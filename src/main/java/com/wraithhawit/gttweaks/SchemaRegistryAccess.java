package com.wraithhawit.gttweaks;

import brachy.modularui.utils.RegistryAccessContainer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Gives ModularUI the connected server's registries on a dedicated server, so multiblock previews
 * stop crashing the client there.
 *
 * <p>ModularUI keeps one {@code RegistryAccessContainer.current()} and only ever updates it from
 * server-side hooks: {@code WorldLoader.load}, {@code ReloadableServerResources}'s constructor and
 * {@code AddReloadListenerEvent}. A client joined to a dedicated server runs none of them, so
 * {@code current()} stays {@code BUILTIN} — the static registries, with no dimension types or
 * biomes. The first multiblock preview (the Terminal, or EMI's multiblock page) class-loads
 * {@code SchemaLevel}, whose {@code INSTANCE} is built from {@code current()} and looks up the
 * overworld dimension type, and the client dies with
 * {@code ExceptionInInitializerError: Missing registry: minecraft:dimension_type}. Single player
 * never sees it because the integrated server runs those hooks first.
 *
 * <p>Both registries {@code SchemaLevel} reads are synced to the client, so the connection's own
 * registry access has them. {@code ICopy}, which copies stacks through a buffer built on the same
 * {@code current()}, gets the synced registries too. Skipped on an integrated server, which has
 * already supplied the fuller server-side set. The tag context is left alone: nothing in the preview
 * path reads it.
 */
public final class SchemaRegistryAccess {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SchemaRegistryAccess() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SchemaRegistryAccess::onLoggingIn);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        var registries = event.getPlayer().connection.registryAccess();
        RegistryAccessContainer.update(registries, null);
        LOGGER.info("[gttweaks] ModularUI now uses the server's registries (dimension types present: {})",
                registries.registry(Registries.DIMENSION_TYPE).isPresent());
    }
}
