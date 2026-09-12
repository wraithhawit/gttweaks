package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.menu.AbstractMenuButton;
import brachy.modularui.widgets.menu.Menu;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Gives a top-level menu button the siblings it actually has.
 *
 * <p>Opening a menu is supposed to close the others. {@code AbstractMenuButton.onMousePressed} does
 * exactly that — but only through {@code forEachSiblingMenuButton}, and that method looks for
 * siblings in one place:
 *
 * <pre>
 *   Menu&lt;?&gt; menuParent = WidgetTree.findParent(this, Menu.class);
 *   return menuParent != null ? WidgetTree.foreachChild(menuParent, ...) : true;
 * </pre>
 *
 * <p>A button nested <i>inside</i> a menu finds one and its siblings get closed. A button that opens
 * a menu but does not live in one — the whole first row of them — has no {@code Menu} ancestor, so
 * the method returns {@code true} having visited nothing, and the caller is told every sibling
 * agreed. The exclusivity rule is written, reached, and silently a no-op for precisely the buttons a
 * player clicks first.
 *
 * <p>That is the multiblock page's letter row. GregTech builds it in
 * {@code MultiblockPreviewWidget.createPredicateMenus}: one {@code ContextMenuButton} per predicate
 * character, added to a plain {@code Flow.row().name("predicates")} in the main panel. Clicking C
 * and then W leaves both dropdowns on screen. The inner buttons those dropdowns contain
 * ({@code createInnerPredicateMenu}) <i>are</i> inside a {@code Menu} and have always behaved.
 *
 * <p>The fallback scope is the button's own panel. Menus opened from a button live in a separate
 * {@code MenuPanel}, not among the panel's children, so the walk cannot reach into an unrelated
 * dropdown and close a button from the inside — it sees the row it belongs to and nothing deeper.
 * Buttons that do have a {@code Menu} ancestor are left on ModularUI's own path untouched.
 */
@Mixin(AbstractMenuButton.class)
public abstract class MenuButtonSiblingScopeMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean gttweaks$reported;

    @Inject(method = "forEachSiblingMenuButton", at = @At("HEAD"), cancellable = true)
    private void gttweaks$siblingsOfATopLevelButton(Predicate<AbstractMenuButton<?>> test,
                                                    CallbackInfoReturnable<Boolean> cir) {
        final AbstractMenuButton<?> self = (AbstractMenuButton<?>) (Object) this;
        final ModularPanel<?> panel;
        try {
            if (!self.isValid() || WidgetTree.findParent(self, Menu.class) != null) {
                // Inside a menu: ModularUI's own scope is the right one, leave it alone.
                return;
            }
            panel = self.getPanel();
        } catch (Throwable t) {
            return;
        }
        if (panel == null) {
            return;
        }
        final boolean result = WidgetTree.foreachChild((IWidget) panel, w -> {
            if (w instanceof AbstractMenuButton<?> sibling && sibling != self) {
                return test.test(sibling);
            }
            return true;
        }, false);
        cir.setReturnValue(result);
        if (!gttweaks$reported) {
            gttweaks$reported = true;
            LOGGER.info("[gttweaks] a menu button with no enclosing Menu is now checking the other "
                    + "menu buttons in its panel; ModularUI skipped that check entirely, which is "
                    + "why several dropdowns could be open at once");
        }
    }
}
