package com.wraithhawit.gttweaks.mixin;

import com.wraithhawit.gttweaks.EmbeddedScreens;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Gives visible GregTech dropdowns one input dispatch and the lifetime of their EMI page. */
@Mixin(RecipeScreen.class)
public abstract class EmiMenuOutsideClickMixin extends Screen {
    @Shadow(remap = false) private List<WidgetGroup> currentPage;

    protected EmiMenuOutsideClickMixin(Component title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gttweaks$dispatchClick(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        final Boolean handled = EmbeddedScreens.dispatchClick(
                EmbeddedScreens.onPage(this.currentPage), mouseX, mouseY, button);
        // Also cancel on false: UIWrapperWidget must not receive the same press again.
        if (handled != null) cir.setReturnValue(handled);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void gttweaks$dismissBeforeLeaving(int key, int scanCode, int modifiers,
                                               CallbackInfoReturnable<Boolean> cir) {
        for (var entry : EmbeddedScreens.onPage(this.currentPage)) {
            if (EmbeddedScreens.hasOpenMenu(entry.screen())) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    EmbeddedScreens.closeMenus(entry.screen());
                    cir.setReturnValue(true);
                    return;
                }
                if (entry.screen().getContext().getFocusedWidget().getElement() != null) {
                    cir.setReturnValue(entry.screen().keyPressed(key, scanCode, modifiers));
                    return;
                }
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void gttweaks$typeIntoMenu(char character, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        for (var entry : EmbeddedScreens.onPage(this.currentPage)) {
            if (EmbeddedScreens.hasOpenMenu(entry.screen())
                    && entry.screen().getContext().getFocusedWidget().getElement() != null) {
                entry.screen().charTyped(character, modifiers);
                // A rejected character in a numeric menu field must not type into EMI search.
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "setPage", at = @At("HEAD"), remap = false)
    private void gttweaks$closeBeforePageChange(int tabPage, int tab, int page, CallbackInfo ci) {
        EmbeddedScreens.closeMenusOnPage(this.currentPage);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void gttweaks$closeBeforeResize(CallbackInfo ci) {
        EmbeddedScreens.closeMenusOnPage(this.currentPage);
    }

    @Override
    public void removed() {
        EmbeddedScreens.closeMenusOnPage(this.currentPage);
        super.removed();
    }
}
