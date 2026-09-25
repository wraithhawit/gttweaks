package com.wraithhawit.gttweaks;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the Terminal's shift-click auto-build work on a dedicated server.
 *
 * <p>GregTech 7.x built the structure on the server, from the pattern
 * ({@code controller.getPattern().autoBuild(player, ...)}). The 8.0 snapshot rewrote the Terminal
 * around a ModularUI preview: the blocks it places are the preview's, with the player's picked
 * hatches and length, and that preview only ever exists on the client — {@code TerminalBehavior}
 * saves it into a field when the screen closes. The server's {@code useOn} finds that field null and
 * passes, so on a dedicated server the client places the blocks locally and the server takes them
 * back half a second later. Single player works only because the integrated server shares the same
 * {@code TerminalBehavior} instance.
 *
 * <p>So the client sends the block list it just built ({@link TerminalBuildClient}) and the server
 * places it, as single player would. The server trusts the list only as far as a creative player is
 * trusted anyway: the sender must be in creative, near an unformed GregTech controller, and every
 * position must be within {@link #MAX_OFFSET} of it. The list is sent in batches because a large
 * multiblock can exceed the serverbound packet size; the last batch forms the structure.
 */
public final class TerminalBuild {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Furthest a placed block may be from the controller on any axis. */
    static final int MAX_OFFSET = 96;
    /** Blocks per packet: a placement is at most 11 bytes, so this stays well under 32 KiB. */
    static final int BATCH_SIZE = 2000;
    /** How far the sender may be from the controller, in blocks. */
    private static final double MAX_SENDER_DISTANCE = 16;

    private TerminalBuild() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional: a client or server without gttweaks still connects, it just builds nothing.
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Payload.TYPE, Payload.CODEC, TerminalBuild::handle);
    }

    /** One block to place, relative to the controller, with its state as a network id. */
    public record Placement(short dx, short dy, short dz, int stateId) {}

    public record Payload(BlockPos controller, boolean last, List<Placement> placements)
            implements CustomPacketPayload {

        public static final Type<Payload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(GTTweaks.MOD_ID, "terminal_build"));

        public static final StreamCodec<ByteBuf, Payload> CODEC = StreamCodec.of(Payload::write, Payload::read);

        private static void write(ByteBuf buf, Payload payload) {
            FriendlyByteBuf out = new FriendlyByteBuf(buf);
            out.writeBlockPos(payload.controller);
            out.writeBoolean(payload.last);
            out.writeVarInt(payload.placements.size());
            for (Placement p : payload.placements) {
                out.writeShort(p.dx);
                out.writeShort(p.dy);
                out.writeShort(p.dz);
                out.writeVarInt(p.stateId);
            }
        }

        private static Payload read(ByteBuf buf) {
            FriendlyByteBuf in = new FriendlyByteBuf(buf);
            BlockPos controller = in.readBlockPos();
            boolean last = in.readBoolean();
            int count = in.readVarInt();
            if (count < 0 || count > BATCH_SIZE) {
                throw new IllegalArgumentException("terminal_build batch of " + count + " placements");
            }
            List<Placement> placements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                placements.add(new Placement(in.readShort(), in.readShort(), in.readShort(), in.readVarInt()));
            }
            return new Payload(controller, last, placements);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handle(Payload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                place(player, payload);
            }
        });
    }

    private static void place(ServerPlayer player, Payload payload) {
        if (!player.isCreative()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos controllerPos = payload.controller();
        if (!level.isLoaded(controllerPos)
                || player.distanceToSqr(controllerPos.getCenter()) > MAX_SENDER_DISTANCE * MAX_SENDER_DISTANCE) {
            return;
        }
        if (!(MetaMachine.getMachine(level, controllerPos) instanceof MultiblockControllerMachine controller)
                || controller.getDefaultPatternState().isFormed()) {
            return;
        }
        for (Placement p : payload.placements()) {
            if (Math.abs(p.dx()) > MAX_OFFSET || Math.abs(p.dy()) > MAX_OFFSET || Math.abs(p.dz()) > MAX_OFFSET) {
                LOGGER.warn("[gttweaks] Terminal build from {} rejected: a block is {} {} {} from the controller",
                        player.getGameProfile().getName(), p.dx(), p.dy(), p.dz());
                return;
            }
        }
        int placed = 0;
        for (Placement p : payload.placements()) {
            BlockPos pos = controllerPos.offset(p.dx(), p.dy(), p.dz());
            BlockState state = Block.stateById(p.stateId());
            if (level.isLoaded(pos)) {
                level.setBlockAndUpdate(pos, state);
                placed++;
            }
        }
        if (payload.last()) {
            controller.getDefaultPatternState().getCache().clear();
            controller.checkAndFormStructure();
        }
        LOGGER.debug("[gttweaks] Terminal build from {} at {}: placed {} block(s){}",
                player.getGameProfile().getName(), controllerPos, placed, payload.last() ? ", last batch" : "");
    }
}
