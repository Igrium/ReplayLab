package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.RotationProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinCamera {
    @Final @Shadow
    private Quaternionf rotation;

    @Inject(method = "update", at = @At("RETURN"))
    void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (focusedEntity instanceof RotationProvider rotProvider) {
            rotProvider.getRotationQuat(rotation);
        }
    }
}
