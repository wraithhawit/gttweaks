package com.wraithhawit.gttweaks.rs.machine;

import java.util.List;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;

import com.wraithhawit.gttweaks.rs.pattern.RSBufferAggregateFluidHandler;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferAggregateItemHandler;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferRecipeHandlerList;
import com.wraithhawit.gttweaks.rs.pattern.RSBufferSlot;
import com.wraithhawit.gttweaks.rs.pattern.RSProxyRecipeHandlerList;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Lends a pattern buffer to a second multiblock.
 *
 * <p>The buffer can only be part of one structure, so without this a network that crafts into a
 * GregTech multiblock would need a buffer, and its patterns, duplicated per machine. A proxy is a
 * part in another structure that points at a buffer elsewhere and passes its multiblock's recipe
 * questions along. It holds no patterns, no ingredients and no network node of its own — it is a
 * relay, and everything it appears to have belongs to the buffer.
 *
 * <p>Linking a proxy earns the buffer one more internal slot, so each multiblock served has somewhere
 * of its own for a pattern's ingredients to sit and they do not queue behind each other.
 *
 * <p><b>No Refined Storage capability is registered for this part</b>, and that is not an oversight.
 * A proxy is not on the network and must not be: it neither stores nor moves resources, and a cable
 * laid against one should see nothing. GregTech's proxy has no grid node for the same reason.
 */
public class RSPatternBufferProxyPartMachine extends TieredIOPartMachine
        implements IMuiMachine, IDataStickInteractable {

    private static final int PROXY_TIER = 6;

    /**
     * Where the buffer is. Persisted, because a proxy has to find its buffer again after a restart,
     * and synced so the client can tell a linked proxy from an unlinked one.
     */
    @SaveField
    @SyncToClient
    @Nullable
    private BlockPos bufferPos;

    @Nullable
    private RSPatternBufferPartMachine buffer;

    /**
     * Whether {@link #buffer} reflects {@link #bufferPos} yet.
     *
     * <p>Separate from a null check because null is a real answer — an unlinked proxy, or one whose
     * buffer is gone — and re-resolving it on every recipe check would mean a {@code getMachine}
     * lookup per tick per multiblock.
     */
    private boolean resolved;

    /**
     * The {@link #bufferPos} that {@link #buffer} was resolved from. On the client {@code bufferPos}
     * arrives by sync, after the first lookup has already answered "not linked"; without this the
     * client kept that answer, built an empty screen while the server built the buffer's, and the
     * mismatch disconnected the player ("36 slots, but expected 63", 2026-09-25).
     */
    @Nullable
    private BlockPos resolvedPos;

    private final RSProxyRecipeHandlerList proxyHandler;

    private final List<RecipeHandlerList> proxyHandlers;

    public RSPatternBufferProxyPartMachine(BlockEntityCreationInfo info) {
        super(info, PROXY_TIER, IO.IN);
        RSBufferAggregateItemHandler aggregateItems =
                attachTrait(new RSBufferAggregateItemHandler(this::bufferSlots));
        RSBufferAggregateFluidHandler aggregateFluids =
                attachTrait(new RSBufferAggregateFluidHandler(this::bufferSlots));
        proxyHandler = new RSProxyRecipeHandlerList(this::bufferHandler, aggregateItems, aggregateFluids);
        proxyHandlers = List.of(proxyHandler);
    }

    private List<RSBufferSlot> bufferSlots() {
        RSPatternBufferPartMachine target = getBuffer();
        return target == null ? List.of() : target.getSlots();
    }

    @Nullable
    private RSBufferRecipeHandlerList bufferHandler() {
        RSPatternBufferPartMachine target = getBuffer();
        return target == null ? null : target.getBufferHandler();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            setBuffer(bufferPos);
        }
    }

    /**
     * Points this proxy at a buffer, or at nothing.
     *
     * <p>A position holding anything other than a pattern buffer resolves to nothing rather than
     * throwing, because the buffer may since have been broken or replaced and a proxy left pointing
     * at a hole should simply stop supplying.
     */
    public void setBuffer(@Nullable BlockPos pos) {
        resolved = true;
        resolvedPos = pos;
        Level level = getLevel();
        if (level == null || pos == null) {
            buffer = null;
            return;
        }
        MetaMachine found = MetaMachine.getMachine((BlockGetter) level, pos);
        if (found instanceof RSPatternBufferPartMachine target) {
            if (!pos.equals(bufferPos)) {
                bufferPos = pos;
                if (!isRemote()) {
                    syncDataHolder.markClientSyncFieldDirty("bufferPos");
                }
            }
            buffer = target;
            // Only the server's buffer counts proxies; the client's copy would grow a slot the
            // server never sent it.
            if (!isRemote()) {
                target.addProxy(getBlockPos());
            }
        } else {
            buffer = null;
        }
    }

    @Nullable
    public RSPatternBufferPartMachine getBuffer() {
        if (!resolved || !java.util.Objects.equals(resolvedPos, bufferPos)) {
            setBuffer(bufferPos);
        }
        return buffer;
    }

    /**
     * Opens only when linked, and then shows the linked buffer's own screen — GregTech's ME proxy does
     * both. An unlinked proxy has nothing of its own to show.
     */
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return getBuffer() != null;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainPanel, PosGuiData data, PanelSyncManager syncManager,
                            UISettings settings) {
        RSPatternBufferPartMachine target = getBuffer();
        if (target != null) {
            target.buildMainUI(mainPanel, data, syncManager, settings);
        }
    }

    @Nullable
    public BlockPos getBufferPos() {
        return bufferPos;
    }

    /**
     * Links this proxy to the buffer whose position the data stick carries -- the stick is written by
     * shift-using it on a buffer, as with GregTech's ME pattern buffer and proxy.
     *
     * <p>Unlike GregTech's, re-linking to a different buffer first gives the old one its slot back,
     * which {@link #setBuffer} alone would not; and a stick pointing at anything but a pattern buffer
     * changes nothing, so a stale stick cannot unlink a working proxy.
     */
    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        BlockPos pos = dataStick.get(GTDataComponents.DATA_COPY_POS.get());
        if (pos == null) {
            return InteractionResult.PASS;
        }
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        if (!(MetaMachine.getMachine((BlockGetter) getLevel(), pos) instanceof RSPatternBufferPartMachine)) {
            player.displayClientMessage(Component.translatable("gttweaks.rs.proxy.no_buffer"), true);
            return InteractionResult.SUCCESS;
        }
        RSPatternBufferPartMachine old = getBuffer();
        if (old != null && !pos.equals(bufferPos)) {
            old.removeProxy(getBlockPos());
        }
        setBuffer(pos);
        player.displayClientMessage(Component.translatable("gttweaks.rs.proxy.linked",
                pos.getX(), pos.getY(), pos.getZ()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return proxyHandlers;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Gives the buffer its slot back. Nothing of this part's is lost, because nothing here was
     * ever its own; the slot the buffer drops is emptied to the network by the buffer itself.
     */
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        RSPatternBufferPartMachine target = getBuffer();
        if (target != null) {
            target.removeProxy(getBlockPos());
        }
    }
}
