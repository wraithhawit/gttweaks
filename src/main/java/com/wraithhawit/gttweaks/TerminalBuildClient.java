package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.mui.MultiblockSchemaInfo;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client half of {@link TerminalBuild}: sends the blocks the Terminal just built from its preview to
 * the server. Only called on the client, and a no-op on an integrated server, where GregTech's own
 * path already works.
 */
public final class TerminalBuildClient {

    private TerminalBuildClient() {}

    /**
     * Sends the blocks exactly as {@code TerminalBehavior.useOn} placed them: each structure block
     * shifted by the controller's position minus its position in the preview.
     */
    public static void send(BlockPos controllerPos, MultiblockSchemaInfo info) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (minecraft.hasSingleplayerServer() || connection == null || info == null
                || info.getStructureBlocks() == null || info.getMapSchema() == null) {
            return;
        }
        if (!connection.hasChannel(TerminalBuild.Payload.TYPE)) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("This server has no gttweaks, so the Terminal cannot build here."), false);
            }
            return;
        }
        BlockPos previewController = info.getMapSchema().getControllerPos();
        List<TerminalBuild.Placement> batch = new ArrayList<>();
        List<List<TerminalBuild.Placement>> batches = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockInfo> entry : info.getStructureBlocks().entrySet()) {
            BlockPos rel = entry.getKey().subtract(previewController);
            if (Math.abs(rel.getX()) > TerminalBuild.MAX_OFFSET || Math.abs(rel.getY()) > TerminalBuild.MAX_OFFSET
                    || Math.abs(rel.getZ()) > TerminalBuild.MAX_OFFSET) {
                continue;
            }
            batch.add(new TerminalBuild.Placement((short) rel.getX(), (short) rel.getY(), (short) rel.getZ(),
                    Block.getId(entry.getValue().getBlockState())));
            if (batch.size() == TerminalBuild.BATCH_SIZE) {
                batches.add(batch);
                batch = new ArrayList<>();
            }
        }
        batches.add(batch);
        for (int i = 0; i < batches.size(); i++) {
            PacketDistributor.sendToServer(
                    new TerminalBuild.Payload(controllerPos, i == batches.size() - 1, batches.get(i)));
        }
    }
}
