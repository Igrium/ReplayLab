package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    /**
     * 26.2 renamed {@code renderLevel} to {@code render} and replaced the live {@code Camera} and the
     * separate projection matrix argument with a single {@link CameraRenderState}.
     */
    @Inject(method = "render", at = @At("HEAD"))
    void onRender(CallbackInfo ci,
                  @Local(argsOnly = true) Matrix4fc modelView,
                  @Local(argsOnly = true) CameraRenderState cameraState) {
        GizmoRenderer.setupCameraProjection(modelView, cameraState);
    }
}
