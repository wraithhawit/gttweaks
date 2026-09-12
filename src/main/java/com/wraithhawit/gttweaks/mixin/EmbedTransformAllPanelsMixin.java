package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.screen.EmbedHandler;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.widget.Widget;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiConsumer;

/**
 * Puts an embedded screen's dropdown menus where their buttons are.
 *
 * <p>{@code EmbedHandler.drawEmbed} draws a ModularUI screen inside someone else's GUI — EMI's
 * recipe screen, in this case — by capturing the current pose matrix, resetting the pose to
 * identity, and handing that matrix to the panel to apply itself:
 *
 * <pre>
 *   graphics.pose().setIdentity();
 *   screen.getMainPanel().transform(applyMatrix);
 * </pre>
 *
 * <p>Note {@code getMainPanel()}. Only the <b>main</b> panel is moved into the host's slot. A
 * dropdown is not part of that panel: {@code AbstractMenuButton} opens a {@code MenuPanel extends
 * ModularPanel} through an {@code IPanelHandler}, and that second panel never receives the matrix.
 * So the button draws where EMI put it while its menu draws at the screen's raw coordinates, which
 * is why the GregTech multiblock page's letter menus land in the top-left corner.
 *
 * <p>The fix hands the same transform to every open panel. {@code PanelManager.getOpenPanels()} is
 * public and reachable from the panel's own screen, so no state is invented — the sub-panels simply
 * get the transform the main one already had.
 *
 * <p>Applied per frame, which is what the main panel gets too, so a menu opened after this frame is
 * positioned on the next one. Panels nested inside the main panel would inherit its transform
 * through their parent and are excluded by identity, but if anything ever double-shifts, this is the
 * first place to look.
 */
@Mixin(EmbedHandler.class)
public abstract class EmbedTransformAllPanelsMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "drawEmbed(Lbrachy/modularui/screen/ModularScreen;Lnet/minecraft/client/gui/GuiGraphics;FLjava/util/function/Predicate;)V",
            at = @At(value = "INVOKE",
                     target = "Lbrachy/modularui/screen/ModularPanel;transform(Ljava/util/function/BiConsumer;)Lbrachy/modularui/widget/Widget;"))
    private static Widget gttweaks$transformEveryPanel(ModularPanel mainPanel, BiConsumer transform) {
        final Widget result = mainPanel.transform(transform);

        final ModularScreen screen = mainPanel.getScreen();
        if (screen == null) {
            return result;
        }
        int moved = 0;
        for (Object open : screen.getPanelManager().getOpenPanels()) {
            if (open instanceof ModularPanel panel && panel != mainPanel) {
                panel.transform(transform);
                moved++;
            }
        }
        if (moved > 0 && !gttweaks$reported) {
            gttweaks$reported = true;
            LOGGER.info("[gttweaks] embedded screen has {} panel(s) besides the main one; giving "
                    + "them the host's transform so menus follow their buttons", moved);
        }
        return result;
    }
}
