package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    public void onRender(CallbackInfo ci,
                         @Local(argsOnly = true, ordinal = 0) Matrix4f modelVIew,
                         @Local(argsOnly = true, ordinal = 1) Matrix4f projectionMatrix,
                         @Local(argsOnly = true) Camera camera) {
        GizmoRenderer.setupCameraProjection(modelVIew, projectionMatrix, camera);
    }
}
