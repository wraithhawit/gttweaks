package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Forces every registered multiblock's structure pattern to build, so broken patterns are found
 * by a headless server run instead of by a player placing a controller.
 *
 * <p>This is the last hole in the porting loop. KubeJS reports success for scripts whose
 * {@code .pattern(...)} callback has not run yet, and that callback is only invoked when a
 * controller's block entity is constructed — on placement, or on chunk load. Every pattern bug in
 * the ATM9 port therefore reached the player: {@code aisle} renamed to {@code slice}, and Rhino
 * handing a whole {@code GTRecipeType[]} to a varargs parameter, both surfaced only in world.
 *
 * <p>{@code MultiblockMachineDefinition} keeps its patterns as {@code Supplier<IBlockPattern>},
 * memoised but not yet resolved, so calling {@code get()} runs exactly the code a placed controller
 * would run — no world, no block entity, no template. Every failure is reported with its machine id
 * rather than stopping at the first, because one run should list everything that is broken.
 *
 * <p>Off unless {@code -Dgttweaks.verifyPatterns=true} is set, so a normal game never pays for it:
 * resolving every pattern eagerly would undo GregTech's laziness for no benefit to a player.
 */
@EventBusSubscriber(modid = GTTweaks.MOD_ID)
public class PatternVerifier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "gttweaks.verifyPatterns";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }

        int checked = 0;
        int failed = 0;
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            if (!(definition instanceof MultiblockMachineDefinition multiblock)) {
                continue;
            }
            Map<String, Supplier<IBlockPattern>> patterns;
            try {
                patterns = multiblock.getStructurePatterns();
            } catch (Throwable t) {
                failed++;
                LOGGER.error("[gttweaks] {} could not list its patterns: {}", definition.getId(), t.toString());
                continue;
            }
            for (Map.Entry<String, Supplier<IBlockPattern>> entry : patterns.entrySet()) {
                checked++;
                try {
                    IBlockPattern pattern = entry.getValue().get();
                    if (pattern == null) {
                        failed++;
                        LOGGER.error("[gttweaks] PATTERN NULL  {} [{}]", definition.getId(), entry.getKey());
                    }
                } catch (Throwable t) {
                    failed++;
                    // The cause carries the useful text — Rhino wraps script errors several deep.
                    Throwable root = t;
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    LOGGER.error("[gttweaks] PATTERN FAILED {} [{}]: {}",
                            definition.getId(), entry.getKey(), root.toString());
                }
            }
        }

        LOGGER.info("[gttweaks] pattern check: {} patterns, {} failed", checked, failed);
        if (failed > 0) {
            LOGGER.error("[gttweaks] {} multiblock pattern(s) would crash on placement", failed);
        }
    }
}
