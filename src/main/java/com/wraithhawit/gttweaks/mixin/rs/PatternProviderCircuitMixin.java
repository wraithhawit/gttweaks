package com.wraithhawit.gttweaks.mixin.rs;

import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.impl.node.patternprovider.PatternProviderNetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;

import com.wraithhawit.gttweaks.rs.circuit.PatternCircuit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * The one place a push knows its pattern. RS's autocrafter and Cable Tiers' tiered autocrafters
 * both use this node, so both get circuits. See {@link PatternCircuit}.
 */
// Priority 900 so ours runs before other mods' callbacks here, which may cancel (Refined Fluid Substitution mixes in too).
@Mixin(value = PatternProviderNetworkNode.class, remap = false, priority = 900)
public abstract class PatternProviderCircuitMixin {
    private static final String ACCEPT = "accept(Lcom/refinedmods/refinedstorage/api/autocrafting/Pattern;Ljava/util/Collection;"
            + "Lcom/refinedmods/refinedstorage/api/core/Action;)"
            + "Lcom/refinedmods/refinedstorage/api/autocrafting/task/ExternalPatternSink$Result;";

    @Inject(method = ACCEPT, at = @At("HEAD"))
    private void gttweaks$openPush(Pattern pattern, Collection<ResourceAmount> resources, Action action,
                                   CallbackInfoReturnable<ExternalPatternSink.Result> cir) {
        PatternCircuit.begin(pattern, action);
    }

    @Inject(method = ACCEPT, at = @At("RETURN"))
    private void gttweaks$applyCircuit(Pattern pattern, Collection<ResourceAmount> resources, Action action,
                                       CallbackInfoReturnable<ExternalPatternSink.Result> cir) {
        PatternCircuit.end(cir.getReturnValue());
    }
}
