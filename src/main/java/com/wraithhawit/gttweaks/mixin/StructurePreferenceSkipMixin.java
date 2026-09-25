package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.util.BlockPatternHelper;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Skips a multiblock preview's saved block choice that does not fit, instead of crashing the client.
 *
 * <p>The Terminal and the multiblock preview remember the blocks a player picks, keyed by position
 * in the pattern <em>as rotated for the facing it was built with</em>. {@code MultiblockPreviewWidget}'s
 * constructor builds the preview once with the machine's default facing, and only afterwards does
 * {@code TerminalBehavior} set the real controller's facing and build it again. So on reopening, a
 * choice made for an assembly line facing one way is replayed against the default rotation, lands on
 * a position whose predicate does not take it, and {@code populateWithUserBlockPreferences} throws
 * {@code IllegalStateException: Invalid preference ... for position ...} out of the item's use. The
 * choices are kept in the item behaviour, shared by every Terminal for the rest of the session, so
 * every reopen crashes until the client restarts.
 *
 * <p>This is that method with the two throws turned into skips. The choice stays in the map, so the
 * correctly-rotated pass that follows applies it; a pass it does not fit just builds that position
 * from the pattern, as if nothing had been chosen there.
 */
@Mixin(value = BlockPatternHelper.class, remap = false)
public abstract class StructurePreferenceSkipMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    private char[][][] flattenedBlockPattern;

    @Shadow
    private static Vec3i getDimensions(char[][][] pattern) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isValidCandidate(Map<BlockPos, BlockInfo> resultStructure, MultiPredicate predicate,
                                     BlockPos pos, BlockInfo newInfo, Direction sliceDir) {
        throw new AssertionError();
    }

    @Inject(method = "populateWithUserBlockPreferences", at = @At("HEAD"), cancellable = true)
    private void gttweaks$skipUnfitPreferences(Map<BlockPos, BlockInfo> resultStructure, IBlockPattern pattern,
                                               Long2ObjectMap<BlockInfo> userBlockPreferences,
                                               Direction frontFacing, Direction upFacing, boolean isFlipped,
                                               CallbackInfo ci) {
        BlockPattern blockPattern = (BlockPattern) pattern;
        Vec3i dimensions = getDimensions(flattenedBlockPattern);
        Direction sliceDir = blockPattern.getDirections()[0].getRelativeFacing(frontFacing, upFacing, isFlipped);
        int skipped = 0;
        for (Long2ObjectMap.Entry<BlockInfo> preference : userBlockPreferences.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(preference.getLongKey());
            BlockInfo blockInfo = preference.getValue();
            if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= dimensions.getX()
                    || pos.getY() >= dimensions.getY() || pos.getZ() >= dimensions.getZ()) {
                skipped++;
                continue;
            }
            MultiPredicate predicate = blockPattern.getPredicates()
                    .get(flattenedBlockPattern[pos.getX()][pos.getY()][pos.getZ()]);
            if (!isValidCandidate(resultStructure, predicate, pos, blockInfo, sliceDir)) {
                skipped++;
                continue;
            }
            resultStructure.put(pos, blockInfo);
        }
        if (skipped > 0) {
            LOGGER.debug("[gttweaks] Multiblock preview: skipped {} saved block choice(s) that do not fit "
                    + "this facing or size", skipped);
        }
        ci.cancel();
    }
}
