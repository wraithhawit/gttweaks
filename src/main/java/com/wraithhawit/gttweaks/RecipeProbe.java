package com.wraithhawit.gttweaks;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reports, for named recipe ids, whether each reached the recipe loader and whether it survived it.
 *
 * <p>For recipes that vanish without a log line. Two checkpoints: {@link #reportRawJson} runs when the
 * recipe manager has finished reading datapack files (before KubeJS, AlmostUnified or any condition
 * sees them), and {@link #onServerStarted} checks the finished recipe manager. Present in the raw
 * files but absent at the end means something in the load pipeline dropped it; absent in the raw
 * files means no datapack provided it.
 *
 * <p>Off unless ids are given, either as {@code -Dgttweaks.probeRecipes=ns:path,ns:path} or one id per
 * line in {@code config/gttweaks-probe-recipes.txt} — the file exists so a player's instance can be
 * probed without editing launcher JVM arguments.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.probeRecipes";
    private static final String FILE = "gttweaks-probe-recipes.txt";

    static List<ResourceLocation> ids() {
        final List<String> raw = new ArrayList<>();
        for (String id : System.getProperty(PROPERTY, "").split(",")) {
            raw.add(id);
        }
        final Path file = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (Files.isRegularFile(file)) {
            try {
                raw.addAll(Files.readAllLines(file));
            } catch (IOException e) {
                LOGGER.warn("[gttweaks] recipe probe: could not read {}: {}", file, e.toString());
            }
        }
        final List<ResourceLocation> ids = new ArrayList<>();
        for (String entry : raw) {
            final String id = entry.trim();
            if (id.isEmpty() || id.startsWith("#")) {
                continue;
            }
            final ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                LOGGER.warn("[gttweaks] recipe probe: '{}' is not a valid id", id);
            } else if (!ids.contains(location)) {
                ids.add(location);
            }
        }
        return ids;
    }

    /** Called with the recipe manager's freshly read datapack files, before anything else processes them. */
    public static void reportRawJson(Map<ResourceLocation, JsonElement> files) {
        final List<ResourceLocation> ids = ids();
        if (ids.isEmpty() || files == null) {
            return;
        }
        LOGGER.info("[gttweaks] recipe probe (raw datapack files): {} recipe files read", files.size());
        for (ResourceLocation id : ids) {
            final JsonElement json = files.get(id);
            if (json == null) {
                LOGGER.info("[gttweaks] recipe probe (raw datapack files): {} ABSENT", id);
                continue;
            }
            final String text = json.toString();
            LOGGER.info("[gttweaks] recipe probe (raw datapack files): {} PRESENT, {} chars, conditions: {}",
                    id, text.length(), json.isJsonObject() && json.getAsJsonObject().has("neoforge:conditions")
                            ? json.getAsJsonObject().get("neoforge:conditions").toString() : "none");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event) {
        final List<ResourceLocation> ids = ids();
        if (ids.isEmpty()) {
            return;
        }
        final MinecraftServer server = event.getServer();
        for (ResourceLocation id : ids) {
            final boolean present = server.getRecipeManager().byKey(id).isPresent();
            LOGGER.info("[gttweaks] recipe probe: {} {}", id, present ? "PRESENT" : "ABSENT");
        }
    }
}
