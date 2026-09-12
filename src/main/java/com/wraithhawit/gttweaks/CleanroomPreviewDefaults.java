package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/** Changes the default example only; the actual cleanroom pattern remains untouched. */
public final class CleanroomPreviewDefaults {
    private CleanroomPreviewDefaults() {}

    public static void apply(AbstractStructureHelper helper, Map<BlockPos, BlockInfo> blocks,
                             IBlockPattern pattern, Direction front, Direction up, boolean flipped) {
        // Limit this to the ordinary upright cleanroom and an entirely uncustomized preview.
        if (up != Direction.UP || !helper.getBlockPreferences().isEmpty()
                || !helper.getMinMaxPreferences().isEmpty()
                || blocks.values().stream().noneMatch(info -> isBlock(info, "gtceu:cleanroom"))) {
            return;
        }

        int replacedGlass = 0;
        for (var entry : new ArrayList<>(blocks.entrySet())) {
            if (!isBlock(entry.getValue(), "gtceu:cleanroom_glass")) continue;
            MultiPredicate allowed = helper.getPredicateFromPos(pattern, entry.getKey(), front, up, flipped);
            BlockInfo casing = candidate(allowed, "gtceu:plastcrete");
            if (casing != null) {
                blocks.put(entry.getKey(), casing);
                replacedGlass++;
            }
        }

        // Use GT's own energy ability candidates instead of guessing hatch registry names/tiers.
        MultiPredicate energy = Predicates.abilities(PartAbility.INPUT_ENERGY);
        int floorY = blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        var floorPositions = blocks.keySet().stream()
                .filter(pos -> pos.getY() == floorY)
                .sorted(Comparator.comparingInt((BlockPos pos) -> Math.abs(pos.getX()) + Math.abs(pos.getZ()))
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .toList();
        int movedHatches = 0;
        for (var entry : new ArrayList<>(blocks.entrySet())) {
            BlockPos source = entry.getKey();
            BlockInfo hatch = entry.getValue();
            if (source.getY() == floorY || !offers(energy, hatch.getBlockState().getBlock())) continue;
            BlockInfo casing = candidate(helper.getPredicateFromPos(pattern, source, front, up, flipped),
                    "gtceu:plastcrete");
            if (casing == null) continue;
            for (BlockPos target : floorPositions) {
                if (!isBlock(blocks.get(target), "gtceu:plastcrete")) continue;
                if (!offers(helper.getPredicateFromPos(pattern, target, front, up, flipped),
                        hatch.getBlockState().getBlock())) continue;
                blocks.put(source, casing);
                blocks.put(target, hatch);
                movedHatches++;
                break;
            }
        }
        LogUtils.getLogger().info("[gttweaks] cleanroom preview defaults: {} glass blocks replaced, "
                + "{} energy hatches moved to floor y={}", replacedGlass, movedHatches, floorY);
    }

    private static boolean isBlock(BlockInfo info, String id) {
        return info != null && BuiltInRegistries.BLOCK.getKey(info.getBlockState().getBlock()).toString().equals(id);
    }

    private static BlockInfo candidate(MultiPredicate predicate, String id) {
        if (predicate == null) return null;
        for (var base : predicate.expand()) {
            for (BlockInfo info : base.getCandidates()) {
                if (isBlock(info, id)) return info;
            }
        }
        return null;
    }

    private static boolean offers(MultiPredicate predicate, Block block) {
        if (predicate == null) return false;
        for (var base : predicate.expand()) {
            for (BlockInfo info : base.getCandidates()) {
                if (info.getBlockState().getBlock() == block) return true;
            }
        }
        return false;
    }
}
