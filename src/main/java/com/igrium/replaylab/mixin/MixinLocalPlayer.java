package com.igrium.replaylab.mixin;

import com.igrium.replaylab.entity.AnimatedCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Final @Shadow
    protected Minecraft minecraft;

    // Allows movement controls to work if viewing replay camera
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    void isCamera(CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.getCameraEntity() instanceof AnimatedCameraEntity) {
            cir.setReturnValue(true);
        }
    }
}
