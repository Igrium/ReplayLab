package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    void onRender(CallbackInfo ci,
                         @Local(argsOnly = true, ordinal = 0) Matrix4f modelVIew,
                         @Local(argsOnly = true, ordinal = 1) Matrix4f projectionMatrix,
                         @Local(argsOnly = true) Camera camera) {
        GizmoRenderer.setupCameraProjection(modelVIew, projectionMatrix, camera);
    }


}
