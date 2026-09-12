package com.wraithhawit.gttweaks;

import brachy.modularui.screen.ModularScreen;

import java.lang.ref.WeakReference;

/**
 * Remembers the ModularUI screen currently embedded in someone else's GUI.
 *
 * <p>Needed because the two halves of the menu-closing fix sit in different mods:
 * {@code EmbedTransformAllPanelsMixin} sees the embedded screen every frame inside ModularUI's
 * {@code EmbedHandler}, while the click that ought to close a menu arrives at EMI's
 * {@code RecipeScreen}, which knows nothing about it.
 *
 * <p>Held weakly, and only ever read on the render thread: this is a pointer to the screen being
 * drawn, not ownership of it. A stale one is harmless — the reader checks whether any menu is
 * actually open before doing anything.
 */
public final class EmbeddedScreens {

    private static WeakReference<ModularScreen> current = new WeakReference<>(null);

    private EmbeddedScreens() {}

    public static void drawing(ModularScreen screen) {
        if (current.get() != screen) {
            current = new WeakReference<>(screen);
        }
    }

    public static ModularScreen currentlyEmbedded() {
        return current.get();
    }

    /**
     * True when the embedded screen has a panel open beyond its main one — which, for the
     * multiblock page, means a dropdown or one of its sub-menus is showing.
     */
    public static boolean hasOpenMenu(ModularScreen screen) {
        if (screen == null) {
            return false;
        }
        try {
            return screen.getPanelManager().getOpenPanels().size() > 1;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
