package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widgets.menu.AbstractMenuButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Clears a menu button's open flags without going through {@code closeMenu}.
 *
 * <p>{@code closeMenu} does two things at once: it detaches the menu widget and it clears these two
 * booleans. That is fine while everything is still attached, but a button whose panel has already
 * been torn down cannot do the first half — {@code getPanel()} throws on an invalidated widget — so
 * it never reaches the second half either, and the button stays "open" forever. Setting the flags
 * directly is the half that always makes sense.
 */
@Mixin(AbstractMenuButton.class)
public interface MenuButtonStateAccessor {

    @Accessor("open")
    void gttweaks$setOpen(boolean open);

    @Accessor("softOpen")
    void gttweaks$setSoftOpen(boolean softOpen);
}
