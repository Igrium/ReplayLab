package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.FovProvider;
import com.igrium.replaylab.camera.RotationProvider;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow @Final private Camera camera;

    @Shadow @Final
    private MinecraftClient client;

    @Shadow
    private float fovMultiplier;

    @Shadow
    private float lastFovMultiplier;

    @Inject(method = "onCameraEntitySet", at = @At("RETURN"))
    void onCameraEntitySet(@Nullable Entity entity, CallbackInfo ci) {
        if (entity != null) {
            ((AccessorCamera) camera).setCameraY(entity.getStandingEyeHeight());
            ((AccessorCamera) camera).setLastCameraY(entity.getStandingEyeHeight());
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> ci) {
        Entity entity = getCamEnt();
        if (entity instanceof FovProvider fProvider) {
            ci.setReturnValue(fProvider.getFov());
        }
    }

    @Inject(method = "updateFovMultiplier", at = @At("HEAD"), cancellable = true)
    void onUpdateFovMultiplier(CallbackInfo ci) {
        // Don't interpolate FOV if it's driven by animation
        if (getCamEnt() instanceof FovProvider) {
            fovMultiplier = 1;
            lastFovMultiplier = 1;
            ci.cancel();
        }
    }

    @Unique
    private @Nullable Entity getCamEnt() {
        return this.client.getCameraEntity() == null ? this.client.player : this.client.getCameraEntity();
    }

}
