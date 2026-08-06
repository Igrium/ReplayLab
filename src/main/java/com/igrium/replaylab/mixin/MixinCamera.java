package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.FovProvider;
import com.igrium.replaylab.camera.RotationProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 folded {@code Camera.setup} into {@link Camera#update(DeltaTracker)} and moved FOV handling
 * off {@code GameRenderer} onto {@code Camera}, so all three of ReplayLab's camera overrides live here now.
 */
@Mixin(Camera.class)
public class MixinCamera {
    @Final @Shadow
    private Quaternionf rotation;

    @Shadow
    private float fovModifier;

    @Shadow
    private float oldFovModifier;

    @Shadow
    public @Nullable Entity entity() {
        throw new AssertionError();
    }

    /**
     * Override the camera rotation as soon as the vanilla alignment has run, before the cull frustum
     * and perspective are derived from it.
     */
    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    void onUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (entity() instanceof RotationProvider rotProvider) {
            rotProvider.getRotationQuat(rotation);
        }
    }

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    void onCalculateFov(float partialTicks, CallbackInfoReturnable<Float> ci) {
        if (entity() instanceof FovProvider fProvider) {
            ci.setReturnValue(fProvider.getFov());
        }
    }

    @Inject(method = "tickFov", at = @At("HEAD"), cancellable = true)
    void onTickFov(CallbackInfo ci) {
        // Don't interpolate FOV if it's driven by animation
        if (entity() instanceof FovProvider) {
            fovModifier = 1;
            oldFovModifier = 1;
            ci.cancel();
        }
    }
}
