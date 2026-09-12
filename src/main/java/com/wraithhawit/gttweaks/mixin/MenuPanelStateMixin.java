package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.menu.AbstractMenuButton;
import brachy.modularui.widgets.menu.Menu;
import brachy.modularui.widgets.menu.MenuPanel;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Keeps a dropdown's sub-menus to one at a time, and forgets them when the dropdown closes.
 *
 * <p>Two problems, one cause. A {@code MenuPanel} is <b>cached</b>: {@code SecondaryPanel} builds it
 * on the first open and hands back the same instance forever after. Every sub-menu ever opened is
 * added to that panel as a child by {@code openSubMenu}, and the only thing that ever takes one back
 * out is {@code AbstractMenuButton.closeMenu}. So whatever the panel is holding when it closes is
 * what the player sees the next time they open it.
 *
 * <p>That teardown is {@code MenuPanel.onClose -> closeAllMenus}, which walks the menus in tree
 * order and closes each one. Removing a menu <b>disposes</b> it ({@code AbstractParentWidget.remove}
 * calls {@code child.dispose()}), and disposing invalidates every widget inside it — including the
 * buttons that opened the sub-menus further down the list. When the walk reaches one of those, its
 * {@code closeMenu} finds {@code isValid()} false, falls through to {@code getPanelHandler()}, and
 * that calls {@code getPanel()}, which throws on an invalidated widget. The walk dies part-way and
 * everything after it keeps {@code open = true} with its menu still attached to the panel.
 *
 * <p>Detach descendants before ModularUI closes the root and invalidates their source buttons.
 * The cached root remains attached so reopening starts with a usable, empty dropdown branch.
 *
 * <p>The exclusivity half is the same idea applied on the way in. {@code onMousePressed} already
 * closes sibling buttons, but the hover path ({@code onMouseEnterArea -> openMenu(true)}) does not:
 * it only refuses to open when a sibling is <i>hard</i>-open, and relies on {@code onMouseLeaveArea}
 * to tidy up the soft ones. In an embedded screen that leave event is not dependable, which is how a
 * column of sub-menus ends up on screen at once. Closing every menu that is not an ancestor of the
 * one being opened makes the rule hold on every path into {@code openSubMenu}, hover included.
 *
 * @see MenuCloseInvalidWidgetMixin for the guard that keeps the failing walk from crashing outright
 */
@Mixin(MenuPanel.class)
public abstract class MenuPanelStateMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reportedExclusive;
    private static boolean gttweaks$reportedStale;

    /** The menu this panel was built around. It is the panel's reason to exist and always stays. */
    @Unique
    private IWidget gttweaks$rootMenu;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void gttweaks$rememberRootMenu(String name, IWidget menu, CallbackInfo ci) {
        this.gttweaks$rootMenu = menu;
    }

    @Inject(method = "openSubMenu", at = @At("HEAD"))
    private void gttweaks$closeOtherSubMenus(IWidget menuList, CallbackInfo ci) {
        final Set<Object> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.add(menuList);
        keep.add(this.gttweaks$rootMenu);
        if (!gttweaks$collectAncestors(menuList, keep)) {
            // The chain runs through a widget that has been thrown away; closing anything on the
            // strength of a half-read tree would be guessing. Leave it be.
            return;
        }
        final int closed = gttweaks$detachMenusExcept(keep);
        if (closed > 0 && !gttweaks$reportedExclusive) {
            gttweaks$reportedExclusive = true;
            LOGGER.info("[gttweaks] closed {} sub-menu(s) that were still open when another one was "
                    + "opened; only one branch of a dropdown shows at a time", closed);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void gttweaks$forgetSubMenus(CallbackInfo ci) {
        final Set<Object> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        if (this.gttweaks$rootMenu != null) {
            keep.add(this.gttweaks$rootMenu);
        }
        final int stale = gttweaks$detachMenusExcept(keep);
        gttweaks$clearFlags(this.gttweaks$rootMenu);
        if (stale > 0 && !gttweaks$reportedStale) {
            gttweaks$reportedStale = true;
            LOGGER.info("[gttweaks] detached {} sub-menu(s) before closing their root; "
                    + "the next open starts clean", stale);
        }
    }

    /**
     * Walks from a menu up through the buttons and menus that own it, adding each to {@code keep}.
     *
     * @return false if the walk hit an invalidated widget, in which case {@code keep} is not to be
     *         trusted — {@code getParent()} throws on those rather than ending the walk.
     */
    @Unique
    private boolean gttweaks$collectAncestors(IWidget menuList, Set<Object> keep) {
        if (!(menuList instanceof Menu<?> menu)) {
            return false;
        }
        try {
            AbstractMenuButton<?> source = ((MenuSourceAccessor) (Object) menu).gttweaks$menuSource();
            while (source != null) {
                if (!source.isValid()) {
                    return false;
                }
                final Menu<?> owner = WidgetTree.findParent(source, Menu.class);
                if (owner == null || !keep.add(owner)) {
                    break;
                }
                source = ((MenuSourceAccessor) (Object) owner).gttweaks$menuSource();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Detaches every menu on this panel that is not in {@code keep}, deepest first.
     *
     * <p>Order matters: removing a menu disposes the buttons inside it, so a menu opened <i>by</i>
     * one of those buttons has to be dealt with before its owner disappears. Sub-menus are appended
     * to the panel in the order they were opened, and the tree walk preserves that, so running the
     * list backwards is running it from the deepest outwards.
     */
    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private int gttweaks$detachMenusExcept(Set<Object> keep) {
        final MenuPanel self = (MenuPanel) (Object) this;
        final List<Menu> menus;
        try {
            menus = WidgetTree.flatListByType(self, Menu.class);
        } catch (Throwable t) {
            return 0;
        }
        int detached = 0;
        for (int i = menus.size() - 1; i >= 0; i--) {
            final Menu<?> menu = menus.get(i);
            if (keep.contains(menu)) {
                continue;
            }
            gttweaks$clearFlags(menu);
            try {
                if (self.remove(menu)) {
                    detached++;
                }
            } catch (Throwable ignored) {
                // Already gone, which is the state we were after anyway.
            }
        }
        return detached;
    }

    /** Marks a menu's button closed, whether or not the menu widget itself survived. */
    @Unique
    private void gttweaks$clearFlags(IWidget menuList) {
        if (!(menuList instanceof Menu<?> menu)) {
            return;
        }
        final AbstractMenuButton<?> source = ((MenuSourceAccessor) (Object) menu).gttweaks$menuSource();
        if (source == null) {
            return;
        }
        final MenuButtonStateAccessor state = (MenuButtonStateAccessor) (Object) source;
        state.gttweaks$setOpen(false);
        state.gttweaks$setSoftOpen(false);
    }
}
