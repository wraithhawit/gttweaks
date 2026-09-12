package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the recipes that actually landed in each GregTech machine's recipe type, so a recipe
 * script that registered nothing is caught by a headless run instead of by opening the machine in
 * world.
 *
 * <p>This is the recipe half of what {@link PatternVerifier} does for structures, and it exists for
 * the same reason: KubeJS reporting "0 errors" only means every script *parsed*. A recipe whose
 * output item does not resolve is dropped with a log line while the script still reports success,
 * so the failure being hunted is a machine that forms perfectly and has an empty JEI page.
 *
 * <p>Counts come from {@code MachineDefinition.getRecipeTypes()}, never from iterating
 * {@code GTRegistries.RECIPE_TYPES}. The first version of this class did iterate that registry and
 * reported 623 of 625 machines empty on a server that had just loaded 41,754 recipes: GregTech's
 * own recipe types are not in it — only the ones KubeJS creates are — so every stock machine fell
 * through to a default of zero. The registry is still used, but only to *label* a type, and only
 * when it has a key there; {@code getRegistryName()} is what actually identifies one.
 *
 * <p>Reported per machine because "which machine is empty" is the question being asked. A machine
 * is only called empty when every one of its recipe types is empty.
 *
 * <p><b>What this cannot tell you.</b> The headless run stages six mods. Recipes referencing items
 * from any other mod are dropped as unresolvable, which is indistinguishable here from a recipe
 * that was never written. A zero means "look at it", not "it is broken" — unless the machine's
 * recipes use only GregTech and vanilla ingredients. Comparing a count against the same count from
 * before a port is the reliable signal.
 *
 * <p>Off unless {@code -Dgttweaks.verifyRecipes=true} is set.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class RecipeVerifier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.verifyRecipes";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }

        final MinecraftServer server = event.getServer();
        final Map<GTRecipeType, Integer> counts = new HashMap<>();
        final List<String> emptyMachines = new ArrayList<>();
        int machines = 0;
        int totalRecipes = 0;

        for (MachineDefinition definition : GTRegistries.MACHINES) {
            final GTRecipeType[] types = definition.getRecipeTypes();
            if (types == null || types.length == 0) {
                continue;
            }
            machines++;
            int total = 0;
            final StringBuilder breakdown = new StringBuilder();
            for (GTRecipeType type : types) {
                if (type == null) {
                    continue;
                }
                final int size = counts.computeIfAbsent(type, t -> count(server, t));
                total += size;
                if (breakdown.length() > 0) {
                    breakdown.append(", ");
                }
                breakdown.append(name(type)).append('=').append(size);
            }
            totalRecipes += total;
            if (total == 0) {
                emptyMachines.add(definition.getId() + " [" + breakdown + "]");
            }
            LOGGER.info("[gttweaks] recipes {} -> {} ({})", definition.getId(), total, breakdown);
        }

        LOGGER.info("[gttweaks] recipe check: {} machines with recipe types, {} distinct types, {} recipes reachable, {} empty",
                machines, counts.size(), totalRecipes, emptyMachines.size());
        for (String machine : emptyMachines) {
            LOGGER.warn("[gttweaks] NO RECIPES {}", machine);
        }
    }

    private static int count(MinecraftServer server, GTRecipeType type) {
        try {
            return server.getRecipeManager().getAllRecipesFor(type).size();
        } catch (Throwable t) {
            LOGGER.error("[gttweaks] RECIPE TYPE UNREADABLE {}: {}", name(type), t.toString());
            return 0;
        }
    }

    private static String name(GTRecipeType type) {
        try {
            return String.valueOf(type.getRegistryName());
        } catch (Throwable t) {
            return "unnamed(" + type.getClass().getSimpleName() + ")";
        }
    }
}
