package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ReplayLab;
import com.replaymod.simplepathing.preview.PathPreviewRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathPreviewRenderer.class, remap = false)
public class MixinPathPreviewRenderer {

    @Inject(method = "renderCameraPath", at = @At("HEAD"), cancellable = true)
    void replaylab$renderCameraPath(PoseStack matrixStack, SubmitNodeStorage submitNodeStorage, CallbackInfo ci) {
        if (ReplayLab.getInstance().isEditorOpen()) {
            ci.cancel();
        }
    }
}
