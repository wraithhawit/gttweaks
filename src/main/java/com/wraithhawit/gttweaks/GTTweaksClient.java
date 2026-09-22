package com.wraithhawit.gttweaks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point: gives gttweaks a Config button in the mod list, and keeps multiblock
 * previews from crashing on a dedicated server ({@link SchemaRegistryAccess}).
 */
@Mod(value = GTTweaks.MOD_ID, dist = Dist.CLIENT)
public class GTTweaksClient {

    public GTTweaksClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SchemaRegistryAccess.register();
    }
}
