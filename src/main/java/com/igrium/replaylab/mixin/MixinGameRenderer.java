package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.FovProvider;
import com.igrium.replaylab.camera.RotationProvider;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
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

    @Shadow @Final private Camera mainCamera;

    @Shadow @Final
    private Minecraft minecraft;

    @Shadow
    private float fovModifier;

    @Shadow
    private float oldFovModifier;

    @Inject(method = "checkEntityPostEffect", at = @At("RETURN"))
    void onCameraEntitySet(@Nullable Entity entity, CallbackInfo ci) {
        if (entity != null) {
            ((AccessorCamera) mainCamera).setEyeHeight(entity.getEyeHeight());
            ((AccessorCamera) mainCamera).setEyeHeightOld(entity.getEyeHeight());
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> ci) {
        Entity entity = getCamEnt();
        if (entity instanceof FovProvider fProvider) {
            ci.setReturnValue(fProvider.getFov());
        }
    }

    @Inject(method = "tickFov", at = @At("HEAD"), cancellable = true)
    void onUpdateFovMultiplier(CallbackInfo ci) {
        // Don't interpolate FOV if it's driven by animation
        if (getCamEnt() instanceof FovProvider) {
            fovModifier = 1;
            oldFovModifier = 1;
            ci.cancel();
        }
    }

    @Unique
    private @Nullable Entity getCamEnt() {
        return this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity();
    }

}
