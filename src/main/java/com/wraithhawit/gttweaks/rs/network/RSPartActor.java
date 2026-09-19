package com.wraithhawit.gttweaks.rs.network;

import com.refinedmods.refinedstorage.api.storage.Actor;

/**
 * Who Refined Storage records as having moved a resource when a GregTech part does it.
 *
 * <p>Every insert and extract takes an actor, and the name is not decoration: it is what the Grid's
 * resource history shows, and {@code StorageNetworkComponent.getResources(Class)} groups by the
 * actor's class. One instance per part kind would therefore split the history pointlessly, so all
 * the parts share this one and the name says which mod moved it.
 */
public final class RSPartActor implements Actor {

    public static final RSPartActor INSTANCE = new RSPartActor();

    private RSPartActor() {
    }

    @Override
    public String getName() {
        return "GregTech part";
    }
}
