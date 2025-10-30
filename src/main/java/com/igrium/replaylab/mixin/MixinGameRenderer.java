package com.igrium.replaylab.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow @Final private Camera camera;

    @Inject(method = "onCameraEntitySet", at = @At("RETURN"))
    void replaylab$onCameraEntitySet(@Nullable Entity entity, CallbackInfo ci) {
        if (entity != null) {
            ((AccessorCamera) camera).setCameraY(entity.getStandingEyeHeight());
            ((AccessorCamera) camera).setLastCameraY(entity.getStandingEyeHeight());
        }
    }
}
