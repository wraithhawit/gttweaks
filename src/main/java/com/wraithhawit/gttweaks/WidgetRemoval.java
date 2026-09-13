package com.wraithhawit.gttweaks;

import java.util.List;

public final class WidgetRemoval {
    private WidgetRemoval() {}

    /** Widget.equals in MUI 3.3.1 rejects subclasses, including a Menu compared with itself. */
    public static boolean removeIdentity(List<?> children, Object target) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == target) {
                children.remove(i);
                return true;
            }
        }
        return false;
    }
}
