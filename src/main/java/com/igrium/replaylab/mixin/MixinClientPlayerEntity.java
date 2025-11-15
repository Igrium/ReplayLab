package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.AnimatedCameraEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {
    @Final @Shadow
    protected MinecraftClient client;

    // Allows movement controls to work if viewing replay camera
    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    void isCamera(CallbackInfoReturnable<Boolean> cir) {
        if (client.getCameraEntity() instanceof AnimatedCameraEntity) {
            cir.setReturnValue(true);
        }
    }
}
