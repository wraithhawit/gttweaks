package com.wraithhawit.gttweaks;

import brachy.modularui.integration.emi.recipe.ModularUIEmiRecipe;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.PanelManager;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.widgets.menu.MenuPanel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.wraithhawit.gttweaks.mixin.EmiScreenCacheAccessor;
import com.wraithhawit.gttweaks.mixin.PanelManagerAccessor;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.WidgetGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.mockito.MockedStatic;
import brachy.modularui.ModularUI;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmbeddedScreensTest {
    private static MockedStatic<ModularUI> environment;
    @BeforeAll static void initializeHeadlessWidgetEnvironment() {
        // MUI's font metrics have a server fallback. These are routing tests, not rendering tests.
        environment = mockStatic(ModularUI.class);
    }
    @AfterAll static void releaseEnvironment() { environment.close(); }
    private static final Bounds BOX = new Bounds(100, 100, 200, 180);

    private record Fixture(ModularScreen screen, PanelManager manager, ModularGuiContext context) {
        EmbeddedScreens.Entry entry() { return new EmbeddedScreens.Entry(screen, BOX); }
    }

    private Fixture fixture(boolean menuOpen) {
        final ModularScreen screen = mock(ModularScreen.class);
        final PanelManager manager = mock(PanelManager.class, withSettings().extraInterfaces(PanelManagerAccessor.class));
        final ModularGuiContext context = mock(ModularGuiContext.class);
        when(screen.getOwner()).thenReturn("gtceu");
        when(screen.getPanelManager()).thenReturn(manager);
        when(screen.getContext()).thenReturn(context);
        if (menuOpen) {
            final MenuPanel menu = mock(MenuPanel.class);
            when(menu.isOpen()).thenReturn(true);
            when(manager.getOpenPanels()).thenReturn(List.of(menu));
        } else {
            when(manager.getOpenPanels()).thenReturn(List.of());
        }
        return new Fixture(screen, manager, context);
    }

    @Test void outsideClickDismissesMenuWithoutFallingThroughToEmi() {
        final var f = fixture(true);
        when(f.screen.mousePressed(0)).thenReturn(false);
        assertEquals(Boolean.TRUE, EmbeddedScreens.dispatchClick(List.of(f.entry()), 700, 400, 0));
        verify(f.screen, times(1)).mousePressed(0);
        final var order = inOrder(f.context, f.screen);
        order.verify(f.context).updateState(700, 400, 0);
        order.verify(f.screen).onFrameUpdate();
        order.verify(f.screen).mousePressed(0);
    }

    @Test void unhandledPreviewPressIsStillMarkedAsAlreadyDispatched() {
        final var f = fixture(false);
        assertEquals(Boolean.FALSE, EmbeddedScreens.dispatchClick(List.of(f.entry()), 120, 120, 1));
        verify(f.screen, times(1)).mousePressed(1);
    }

    @Test void unrelatedEmiClickIsUntouched() {
        final var f = fixture(false);
        assertNull(EmbeddedScreens.dispatchClick(List.of(f.entry()), 700, 400, 0));
        verify(f.screen, never()).mousePressed(anyInt());
        verify(f.screen, never()).onFrameUpdate();
    }

    @Test void openDropdownGetsPriorityOverAnotherPreviewUnderThePointer() {
        final var first = fixture(false);
        final var second = fixture(true);
        assertEquals(Boolean.TRUE, EmbeddedScreens.dispatchClick(List.of(first.entry(), second.entry()), 120, 120, 0));
        verify(first.screen, never()).mousePressed(anyInt());
        verify(second.screen, times(1)).mousePressed(0);
    }

    @Test void leavingPageClosesOnlyMenusAndToleratesLiveListChanges() {
        final var f = fixture(false);
        final ModularPanel<?> main = mock(ModularPanel.class);
        final MenuPanel first = mock(MenuPanel.class);
        final MenuPanel second = mock(MenuPanel.class);
        when(first.isOpen()).thenReturn(true);
        when(second.isOpen()).thenReturn(true);
        final List<ModularPanel<?>> panels = new ArrayList<>(List.of(first, second, main));
        when(f.manager.getOpenPanels()).thenReturn(panels);
        doAnswer(call -> { panels.remove(call.getArgument(0)); return null; })
                .when(f.manager).closePanel(any());
        assertTrue(EmbeddedScreens.closeMenus(f.screen));
        assertEquals(List.of(main), panels);
        verify(f.manager, never()).closePanel(main);
        verify((PanelManagerAccessor) f.manager, times(2)).gttweaks$flushPanels();
    }

    @Test void closedCachedMenuIsNotTreatedAsAnOpenDropdown() {
        final var f = fixture(false);
        final MenuPanel closed = mock(MenuPanel.class);
        when(f.manager.getOpenPanels()).thenReturn(List.of(closed));
        assertFalse(EmbeddedScreens.hasOpenMenu(f.screen));
        assertFalse(EmbeddedScreens.closeMenus(f.screen));
        verify(f.manager, never()).closePanel(any());
    }

    @Test void currentPageNeverRoutesToLastDrawnOffPageRecipe() {
        final var visible = fixture(false);
        final var offPage = fixture(true);
        final ModularUIEmiRecipe recipe = mock(ModularUIEmiRecipe.class);
        final ModularUIEmiRecipe other = mock(ModularUIEmiRecipe.class);
        when(recipe.getBounds()).thenReturn(new Bounds(0, 0, 200, 180));
        final LoadingCache<ModularUIEmiRecipe, ModularScreen> cache = CacheBuilder.newBuilder()
                .build(new CacheLoader<>() {
                    public ModularScreen load(ModularUIEmiRecipe key) {
                        throw new AssertionError("Input must not create a screen");
                    }
                });
        cache.put(recipe, visible.screen);
        cache.put(other, offPage.screen);
        final WidgetGroup group = new WidgetGroup(recipe, 100, 100, 200, 180);
        group.widgets.add(new ModularUIEmiRecipe.UIWrapperWidget(recipe));
        try (var mocked = mockStatic(EmiScreenCacheAccessor.class)) {
            mocked.when(EmiScreenCacheAccessor::gttweaks$screenCache).thenReturn(cache);
            final var entries = EmbeddedScreens.onPage(List.of(group));
            assertEquals(1, entries.size());
            assertSame(visible.screen, entries.getFirst().screen());
            assertEquals(BOX, entries.getFirst().bounds());
            EmbeddedScreens.dispatchClick(entries, 120, 120, 0);
            verify(offPage.screen, never()).mousePressed(anyInt());
            assertTrue(EmbeddedScreens.onPage(List.of()).isEmpty());
            cache.invalidate(recipe);
            assertTrue(EmbeddedScreens.onPage(List.of(group)).isEmpty());
        }
    }
}
