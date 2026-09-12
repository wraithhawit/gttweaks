package com.wraithhawit.gttweaks;

import brachy.modularui.integration.emi.recipe.ModularUIEmiRecipe;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.widgets.menu.MenuPanel;
import com.wraithhawit.gttweaks.mixin.EmiScreenCacheAccessor;
import com.wraithhawit.gttweaks.mixin.PanelManagerAccessor;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.WidgetGroup;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;

/** Input belongs to a visible EMI recipe, never to a global last-rendered screen. */
public final class EmbeddedScreens {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedDispatch;
    private static boolean loggedCleanup;
    private EmbeddedScreens() {}

    public record Entry(ModularScreen screen, Bounds bounds) {}

    public static List<Entry> onPage(List<WidgetGroup> page) {
        final List<Entry> entries = new ArrayList<>();
        for (WidgetGroup group : page) {
            if (!(group.recipe instanceof ModularUIEmiRecipe recipe)) continue;
            // Do not construct an unseen screen to handle input or clean up a page.
            final ModularScreen screen = EmiScreenCacheAccessor.gttweaks$screenCache().getIfPresent(recipe);
            if (screen == null || !"gtceu".equals(screen.getOwner())
                    || screen.getPanelManager().isDisposed()) continue;
            for (var widget : group.widgets) {
                if (widget instanceof ModularUIEmiRecipe.UIWrapperWidget) {
                    final Bounds b = widget.getBounds();
                    entries.add(new Entry(screen, new Bounds(group.x() + b.x(), group.y() + b.y(),
                            b.width(), b.height())));
                    break;
                }
            }
        }
        return entries;
    }

    public static boolean hasOpenMenu(ModularScreen screen) {
        return screen.getPanelManager().getOpenPanels().stream()
                .anyMatch(panel -> panel instanceof MenuPanel && panel.isOpen());
    }

    /** Null leaves ordinary EMI input alone; a Boolean means the press was already dispatched. */
    public static Boolean dispatchClick(List<Entry> entries, double mouseX, double mouseY, int button) {
        for (Entry entry : entries) {
            if (hasOpenMenu(entry.screen())) {
                refreshPointer(entry.screen(), mouseX, mouseY);
                entry.screen().mousePressed(button);
                if (!loggedDispatch) {
                    loggedDispatch = true;
                    LOGGER.info("[gttweaks] EMI dropdown click routed to current page with refreshed pointer at {}, {}", mouseX, mouseY);
                }
                // A dismissal must not also activate an EMI recipe behind the dropdown.
                return true;
            }
        }
        for (Entry entry : entries) {
            if (entry.bounds().contains((int) mouseX, (int) mouseY)) {
                refreshPointer(entry.screen(), mouseX, mouseY);
                return entry.screen().mousePressed(button);
            }
        }
        return null;
    }

    /** Rebuild transformed hit areas before reading hover flags to dispatch a click. */
    public static void refreshPointer(ModularScreen screen, double mouseX, double mouseY) {
        final var context = screen.getContext();
        context.reset();
        context.updateState((int) mouseX, (int) mouseY, context.getRenderPartialTicks());
        // Runs panel hit-testing listeners, then hover enter/leave events.
        screen.onFrameUpdate();
        flushPanels(screen);
    }

    public static void flushPanels(ModularScreen screen) {
        ((PanelManagerAccessor) screen.getPanelManager()).gttweaks$flushPanels();
    }

    public static boolean closeMenus(ModularScreen screen) {
        flushPanels(screen);
        boolean closed = false;
        // A close changes the manager's live list. No animation: it cannot finish off-page.
        for (ModularPanel<?> panel : List.copyOf(screen.getPanelManager().getOpenPanels())) {
            if (panel instanceof MenuPanel && panel.isOpen()) {
                screen.getPanelManager().closePanel(panel);
                closed = true;
            }
        }
        flushPanels(screen);
        if (closed && !loggedCleanup) {
            loggedCleanup = true;
            LOGGER.info("[gttweaks] closed cached EMI dropdowns immediately; main preview retained");
        }
        return closed;
    }

    public static void closeMenusOnPage(List<WidgetGroup> page) {
        for (Entry entry : onPage(page)) closeMenus(entry.screen());
    }
}
