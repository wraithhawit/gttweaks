package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.menu.AbstractMenuButton;
import brachy.modularui.widgets.menu.Menu;
import brachy.modularui.widgets.menu.MenuPanel;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Diagnostic: reports what ModularUI's own menu-closing pass decides, and why.
 *
 * <p>{@code ModularScreen.mousePressed} calls {@code closeAllMenus(false, true)} on every open
 * {@code MenuPanel} before dispatching the click anywhere, so in principle any click already closes
 * every menu the mouse is not over. In the embedded preview it demonstrably does not, and the branch
 * that decides is hover state:
 *
 * <pre>
 *   // Menu.checkClose(soft=false, requireNoHover=true)
 *   if (!soft &amp;&amp; !requireNoHover || !menuSource.isBelowMouse() &amp;&amp; !isSelfOrChildHovered())
 * </pre>
 *
 * <p>With {@code requireNoHover} true the first half is false, so everything rests on those two
 * hover tests — and an embedded panel's hover list is computed from geometry the host has since
 * moved. This logs both tests per menu rather than assuming which one is lying.
 *
 * <p>Capped at {@value #LIMIT} lines: this runs on every mouse press. Delete this class once the
 * question is answered.
 */
@Mixin(MenuPanel.class)
public abstract class MenuCloseProbeMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LIMIT = 30;
    private static int gttweaks$logged;

    @Inject(method = "closeAllMenus", at = @At("HEAD"))
    @SuppressWarnings("rawtypes")
    private void gttweaks$reportCloseDecision(boolean soft, boolean requireNoHover, CallbackInfo ci) {
        if (gttweaks$logged >= LIMIT) {
            return;
        }
        gttweaks$logged++;
        final StringBuilder sb = new StringBuilder("[gttweaks] closeAllMenus soft=").append(soft)
                .append(" requireNoHover=").append(requireNoHover);
        try {
            final List<Menu> menus = WidgetTree.flatListByType((MenuPanel) (Object) this, Menu.class);
            sb.append(" menus=").append(menus.size());
            for (Menu<?> menu : menus) {
                final AbstractMenuButton<?> source =
                        ((MenuSourceAccessor) (Object) menu).gttweaks$menuSource();
                sb.append(" | ");
                if (source == null) {
                    sb.append("<no source>");
                    continue;
                }
                sb.append(source)
                        .append(" open=").append(source.isOpen())
                        .append(" soft=").append(source.isSoftOpen())
                        .append(" srcBelowMouse=").append(source.isBelowMouse())
                        .append(" menuHovered=").append(menu.isSelfOrChildHovered());
            }
        } catch (Throwable t) {
            sb.append(" | probe threw: ").append(t);
        }
        LOGGER.info(sb.toString());
    }
}
