package com.igrium.replaylab.mixin;

import com.igrium.craftui.app.AppManager;
import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.render.VideoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Yes I'm mixing into my own library. Bite me.
@Mixin(value = AppManager.class, remap = false)
public class MixinAppManager {
    @Inject(method = "getCustomViewportBounds", at = @At("HEAD"), cancellable = true)
    private static void getCustomViewportBounds(CallbackInfoReturnable<CraftApp.ViewportBounds> ci) {
        if (VideoRenderer.isRenderingVideo()) {
            ci.setReturnValue(null);
        }
    }
}
