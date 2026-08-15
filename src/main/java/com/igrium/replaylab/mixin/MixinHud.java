package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ReplayLab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class MixinHud {

    // Don't draw vignette when in replaylab editor
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    void cancelExtractVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (ReplayLab.getInstance().isEditorOpen()) {
            ci.cancel();
        }
    }

}
