package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widgets.menu.AbstractMenuButton;
import brachy.modularui.widgets.menu.Menu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the button a {@link Menu} belongs to.
 *
 * <p>{@code Menu.menuSource} is private with a package-private setter, and it is the only link from
 * a menu back to the widget that owns its open/closed state. {@link MenuPanelStateMixin} needs that
 * link to tell a stale menu from a live one.
 */
@Mixin(Menu.class)
public interface MenuSourceAccessor {

    @Accessor("menuSource")
    AbstractMenuButton<?> gttweaks$menuSource();
}
