package com.wraithhawit.gttweaks.rs;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;

import com.wraithhawit.gttweaks.rs.network.RSNetworkPart;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Makes the parts visible to Refined Storage.
 *
 * <p>This is the line without which nothing else here does anything. Refined Storage does not scan
 * for nodes: {@code PlatformImpl.getContainerProviderSafely} resolves this block capability against
 * the block entity at each neighbouring position, so a cable laid against a part that does not
 * advertise it finds an ordinary block. The node inside {@code RSNodeHolder} would exist, hold its
 * container, and never be on any network. rsmbac learned this the same way.
 *
 * <p>Asked of the part through {@link RSNetworkPart} rather than a cast to the item bus base: the
 * fluid hatches will extend GregTech's fluid part class instead, and a cast would compile and then
 * throw the first time a cable touched one.
 *
 * <p>Side-independent, unlike GregTech's AE2 parts, which expose their grid node only on the face
 * the part looks out of. Refined Storage has no equivalent notion of an exposed side on the
 * container, and a multiblock part has exactly one face that is not buried in the structure anyway,
 * so filtering by direction could only be a way to get that face wrong.
 */
public final class RSCapabilities {

    private RSCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        registerPart(event, RSMachines.RS_INPUT_BUS.getBlockEntityType());
    }

    @SuppressWarnings("unchecked")
    private static void registerPart(RegisterCapabilitiesEvent event,
                                     BlockEntityType<? extends MetaMachine> type) {
        event.registerBlockEntity(
                RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                (BlockEntityType<MetaMachine>) type,
                (blockEntity, direction) -> blockEntity instanceof RSNetworkPart part
                        ? part.getNodeHolder().containerProvider()
                        : null);
    }
}
