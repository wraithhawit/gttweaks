package com.wraithhawit.gttweaks.mixin;

import brachy.modularui.widget.AbstractParentWidget;
import com.wraithhawit.gttweaks.WidgetRemoval;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Preserve MUI's disposal and child-removal callbacks; replace only the failing list lookup. */
@Mixin(value = AbstractParentWidget.class, remap = false)
public abstract class WidgetRemovalMixin {
    @Redirect(method = "remove(Lbrachy/modularui/api/widget/IWidget;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"))
    private boolean gttweaks$removeByIdentity(List<?> children, Object child) {
        return WidgetRemoval.removeIdentity(children, child);
    }
}
