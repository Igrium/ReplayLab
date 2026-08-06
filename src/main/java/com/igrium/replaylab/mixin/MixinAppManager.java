package com.igrium.replaylab.mixin;

import com.igrium.craftui.app.AppManager;
import com.igrium.craftui.app.CraftApp;
import com.igrium.replaylab.render.VideoRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /**
     * <code>compositeViewportTarget</code> reads the <code>currentViewportBounds</code> field directly,
     * so overriding {@link AppManager#getCustomViewportBounds()} isn't enough to keep it out of the
     * export loop. Since <code>preRender</code> never runs during export, that field keeps its last
     * in-game value and the compositor would hijack <code>mainRenderTarget</code> out from under
     * ReplayMod's gui framebuffer.
     */
    @Inject(method = "compositeViewportTarget", at = @At("HEAD"), cancellable = true)
    private static void cancelComposite(Minecraft client, CallbackInfo ci) {
        if (VideoRenderer.isRenderingVideo())
            ci.cancel();
    }
}
