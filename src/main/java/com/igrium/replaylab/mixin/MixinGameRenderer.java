package com.igrium.replaylab.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow @Final private Camera mainCamera;

    @Inject(method = "checkEntityPostEffect", at = @At("RETURN"))
    void onCameraEntitySet(@Nullable Entity entity, CallbackInfo ci) {
        if (entity != null) {
            ((AccessorCamera) mainCamera).setEyeHeight(entity.getEyeHeight());
            ((AccessorCamera) mainCamera).setEyeHeightOld(entity.getEyeHeight());
        }
    }
}
