package com.wraithhawit.gttweaks.mixin;

import com.gregtechceu.gtceu.api.mui.MultiblockSchemaInfo;
import com.gregtechceu.gtceu.common.item.behavior.TerminalBehavior;
import com.wraithhawit.gttweaks.TerminalBuild;
import com.wraithhawit.gttweaks.TerminalBuildClient;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After the Terminal builds a structure on the client, sends that structure to the server; see
 * {@link TerminalBuild}.
 *
 * <p>{@code useOn} returns {@code SUCCESS} on the client only once it has placed the preview's blocks
 * — after its shift, creative, unformed-controller and non-empty-preview checks — so the return is
 * the point where the client-side build is known to have happened, and the server needs the same.
 */
@Mixin(value = TerminalBehavior.class, remap = false)
public class TerminalServerBuildMixin {

    @Shadow
    private MultiblockSchemaInfo multiblockSchemaInfo;

    @Inject(method = "useOn", at = @At("RETURN"))
    private void gttweaks$sendBuildToServer(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide() && cir.getReturnValue() == InteractionResult.SUCCESS) {
            TerminalBuildClient.send(context.getClickedPos(), multiblockSchemaInfo);
        }
    }
}
