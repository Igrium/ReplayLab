package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ReplayLab;
import com.replaymod.replay.camera.CameraEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CameraEntity.class, remap = false)
public class MixinCameraEntity {

    // Fix spectating on click weirdness
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    void onHandleInputEvents(CallbackInfo ci) {
        if (ReplayLab.getInstance().isEditorOpen()) {
            ci.cancel();
        }
    }
}
