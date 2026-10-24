package com.wraithhawit.gttweaks.rs.treedump;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.wraithhawit.gttweaks.rs.machine.RSPatternBufferPartMachine;
import com.wraithhawit.rsapg.api.TreeDumpApi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * GregTech knowledge for rsapg's EMI recipe-tree dump (loaded only when rsapg is installed):
 * <ul>
 *   <li>a GregTech machine answers with its own id and tier; a multiblock part (input bus, hatch,
 *   RS Pattern Buffer...) answers with its formed controllers' ids and their tier, so an autocrafter
 *   facing an Electric Blast Furnace's input bus takes blast furnace patterns;</li>
 *   <li>a GregTech recipe's tier is its voltage tier ({@link RecipeHelper#getRecipeEUtTier}), so a
 *   pattern goes to the lowest-tier machine that can run it;</li>
 *   <li>gttweaks' RS Pattern Buffer is itself a pattern holder for its multiblock.</li>
 * </ul>
 */
public final class GTTreeDump {
    private static final Logger LOGGER = LoggerFactory.getLogger("gttweaks/rs-treedump");

    private GTTreeDump() {
    }

    public static void register() {
        TreeDumpApi.registerMachineResolver(GTTreeDump::machine);
        TreeDumpApi.registerRecipeTierResolver(GTTreeDump::recipeTier);
        TreeDumpApi.registerPatternHolderResolver(GTTreeDump::patternBuffer);
        LOGGER.info("[gttweaks] rsapg recipe-tree dump: GregTech machines, tiers and RS Pattern Buffers registered");
    }

    @Nullable
    static TreeDumpApi.MachineInfo machine(ServerLevel level, BlockEntity be) {
        if (!(be instanceof MetaMachine machine)) {
            return null;
        }
        if (machine instanceof MultiblockPartMachine part) {
            return controllers(part);
        }
        int tier = machine instanceof ITieredMachine tiered ? tiered.getTier() : -1;
        return new TreeDumpApi.MachineInfo(List.of(machine.getDefinition().getId()), tier);
    }

    /** A part stands in for its formed controllers; the tier is the best of theirs. */
    static TreeDumpApi.MachineInfo controllers(MultiblockPartMachine part) {
        List<ResourceLocation> ids = new ArrayList<>();
        int tier = -1;
        for (MultiblockControllerMachine controller : part.getControllers()) {
            if (!controller.isFormed()) {
                continue;
            }
            ids.add(controller.getDefinition().getId());
            if (controller instanceof ITieredMachine tiered) {
                tier = Math.max(tier, tiered.getTier());
            }
        }
        if (ids.isEmpty()) {
            // Unformed: still answer with the part itself so the plan says why rather than guessing.
            ids.add(part.getDefinition().getId());
        }
        return new TreeDumpApi.MachineInfo(List.copyOf(ids), tier);
    }

    static int recipeTier(ServerLevel level, ResourceLocation recipeId) {
        return level.getRecipeManager().byKey(recipeId)
                .map(holder -> holder.value() instanceof GTRecipe recipe ? RecipeHelper.getRecipeEUtTier(recipe) : -1)
                .orElse(-1);
    }

    @Nullable
    static TreeDumpApi.PatternHolder patternBuffer(ServerLevel level, BlockEntity be) {
        if (!(be instanceof RSPatternBufferPartMachine buffer)) {
            return null;
        }
        CustomItemStackHandler patterns = buffer.getPatternInventory();
        TreeDumpApi.MachineInfo fed = controllers(buffer);
        String name = buffer.getBlockState().getBlock().getName().getString();
        return new TreeDumpApi.PatternHolder() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int freeSlots() {
                int free = 0;
                for (int slot = 0; slot < patterns.getSlots(); slot++) {
                    if (patterns.getStackInSlot(slot).isEmpty()) {
                        free++;
                    }
                }
                return free;
            }

            @Override
            public TreeDumpApi.MachineInfo machine() {
                return fed;
            }

            @Override
            public boolean place(ItemStack pattern) {
                for (int slot = 0; slot < patterns.getSlots(); slot++) {
                    if (patterns.getStackInSlot(slot).isEmpty() && patterns.insertItem(slot, pattern, true).isEmpty()) {
                        patterns.insertItem(slot, pattern, false);
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
