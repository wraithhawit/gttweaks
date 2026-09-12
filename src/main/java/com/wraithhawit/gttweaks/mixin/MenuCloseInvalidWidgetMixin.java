package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widget.AbstractWidget;
import brachy.modularui.widgets.menu.AbstractMenuButton;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops closing a menu whose button has already been thrown away from killing the client.
 *
 * <p>Opening a sub-menu on the multiblock page and then tabbing away crashed with:
 *
 * <pre>
 *   IllegalStateException: ContextMenuButton#MutableBlockPos{x=-7, y=-12, z=7} is not in a valid state!
 *     at AbstractWidget.getParent          (throws whenever isValid() is false)
 *     at TreeUtil.findParent
 *     at AbstractMenuButton.checkClose
 *     at MenuPanel.closeAllMenus
 * </pre>
 *
 * <p>{@code closeAllMenus} walks its menu buttons and each one calls {@code findParent}, which
 * climbs the tree through {@code getParent()} — and that method throws rather than returning null
 * once a widget has been invalidated. So a button belonging to a panel that has already gone takes
 * the whole client down while the UI is doing nothing more dangerous than tidying up.
 *
 * <p>This is ModularUI's own bug and not something gttweaks introduced, but
 * {@code EmbedTransformAllPanelsMixin} is what put these menus back under their buttons and made
 * them usable, so it is reachable now in a way it was not before. A widget that is no longer valid
 * has nothing to close: skipping it is what the loop would want anyway.
 *
 * <p>{@code closeMenu} has the same hole one step earlier, and it is the one that actually strands
 * state. {@code Menu.checkClose} calls {@code closeMenu} first, and an invalidated button there
 * takes the {@code getPanelHandler()} branch, which builds a handler from {@code getPanel()} — the
 * same throwing method. That aborts {@code MenuPanel.closeAllMenus} half-way through its list, so
 * every menu after it keeps {@code open = true} and stays attached to the cached panel. Clearing the
 * two flags and returning is the part of {@code closeMenu} that still means something for a widget
 * with no panel left to detach from; {@link MenuPanelStateMixin} deals with what stays attached.
 *
 * <p>Logged once, because a close path that runs on every click is exactly where a per-event log
 * line becomes a hundred megabytes.
 */
@Mixin(AbstractMenuButton.class)
public abstract class MenuCloseInvalidWidgetMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;
    private static boolean gttweaks$reportedClose;

    @Shadow private boolean open;
    @Shadow private boolean softOpen;

    @Inject(method = "checkClose", at = @At("HEAD"), cancellable = true)
    private void gttweaks$skipInvalidButton(boolean a, boolean b, CallbackInfo ci) {
        if (((AbstractWidget) (Object) this).isValid()) {
            return;
        }
        ci.cancel();
        if (!gttweaks$reported) {
            gttweaks$reported = true;
            LOGGER.info("[gttweaks] skipped closing a menu button that was already invalidated; "
                    + "ModularUI's getParent() throws on those and takes the client with it");
        }
    }

    @Inject(method = "closeMenu", at = @At("HEAD"), cancellable = true)
    private void gttweaks$closeInvalidButtonByFlag(boolean soft, CallbackInfo ci) {
        if (((AbstractWidget) (Object) this).isValid()) {
            return;
        }
        this.open = false;
        this.softOpen = false;
        ci.cancel();
        if (!gttweaks$reportedClose) {
            gttweaks$reportedClose = true;
            LOGGER.info("[gttweaks] marked an invalidated menu button closed without asking it for "
                    + "a panel it no longer has; closeAllMenus can finish its list now");
        }
    }
}
