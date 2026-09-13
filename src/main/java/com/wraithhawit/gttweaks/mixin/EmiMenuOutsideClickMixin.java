package com.wraithhawit.gttweaks.mixin;

import com.wraithhawit.gttweaks.EmbeddedScreens;

import brachy.modularui.screen.ModularScreen;
import com.mojang.logging.LogUtils;
import dev.emi.emi.screen.RecipeScreen;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a click outside an embedded dropdown reach the UI that owns it, so the menu can close.
 *
 * <p>An embedded ModularUI screen only receives the clicks EMI dispatches to its widget, and EMI
 * dispatches by bounds: {@code UIWrapperWidget.mouseClicked} is called for clicks inside the preview
 * box and nowhere else. A dropdown pops <i>outside</i> that box, so the one gesture that would
 * dismiss it — clicking away — never arrives. {@code MenuPanel.closeOnOutOfBoundsClick()} returns
 * true, and the logic behind it is fine; it simply never runs.
 *
 * <p>The result was menus that could be opened and never closed, stacking up until
 * {@code closeAllMenus} walked an invalidated button and took the client down — the crash
 * {@link MenuCloseInvalidWidgetMixin} now guards. This addresses the cause rather than the symptom.
 *
 * <p>The click is handed to {@code ModularScreen.mousePressed} first, which is exactly what the
 * in-bounds path does, so ModularUI performs its own hit-testing and decides for itself whether the
 * click fell outside a panel. Nothing here reimplements that test — the earlier attempts at
 * second-guessing this UI's coordinate maths all failed, and this deliberately does not try.
 *
 * <p>Only while a menu is actually open, so ordinary EMI clicking is untouched; and only when
 * ModularUI reports the click consumed does EMI's own handling get skipped.
 */
@Mixin(RecipeScreen.class)
public abstract class EmiMenuOutsideClickMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gttweaks$letMenusSeeOutsideClicks(double mouseX, double mouseY, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        final ModularScreen screen = EmbeddedScreens.currentlyEmbedded();
        if (!EmbeddedScreens.hasOpenMenu(screen)) {
            return;
        }
        final boolean consumed;
        try {
            consumed = screen.mousePressed(button);
        } catch (Throwable t) {
            LOGGER.warn("[gttweaks] embedded screen threw while handling an outside click: {}",
                    t.toString());
            return;
        }
        if (!gttweaks$reported) {
            gttweaks$reported = true;
            LOGGER.info("[gttweaks] forwarding outside clicks to the embedded UI while a menu is "
                    + "open, so it can close itself");
        }
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
