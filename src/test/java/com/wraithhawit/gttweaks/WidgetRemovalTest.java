package com.wraithhawit.gttweaks;

import brachy.modularui.ModularUI;
import brachy.modularui.widgets.menu.Menu;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class WidgetRemovalTest {
    @Test void realMenusReproduceFailedRemovalAndIdentityRemovesOnlyTheTarget() {
        try (MockedStatic<ModularUI> environment = mockStatic(ModularUI.class)) {
            Menu<?> root = new Menu<>();
            Menu<?> first = new Menu<>();
            Menu<?> second = new Menu<>();
            List<Menu<?>> children = new ArrayList<>(List.of(root, first, second));
            assertFalse(first.equals(first), "Confirm the defect in the actual staged MUI class");
            assertFalse(children.remove(first), "This is why stock closeMenu leaves the widget attached");
            assertEquals(3, children.size());
            assertTrue(WidgetRemoval.removeIdentity(children, first));
            assertEquals(2, children.size());
            assertSame(root, children.get(0));
            assertSame(second, children.get(1));
            assertFalse(WidgetRemoval.removeIdentity(children, first));
            assertTrue(WidgetRemoval.removeIdentity(children, second));
            assertSame(root, children.get(0));
            // A cached submenu can be reopened and removed again.
            children.add(first);
            assertTrue(WidgetRemoval.removeIdentity(children, first));
            assertEquals(1, children.size());
        }
    }
}
