package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ReplayLab;
import com.replaymod.core.KeyBindingRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyBindingRegistry.class, remap = false)
public class MixinKeyBindingRegistry {

    @Inject(method = "handleRepeatedKeyBindings", at = @At("HEAD"), cancellable = true)
    void cancelRepeatedKeyBindings(CallbackInfo ci) {
        if (ReplayLab.getInstance().isEditorOpen()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeyBindings", at = @At("HEAD"), cancellable = true)
    void cancelKeyBindings(CallbackInfo ci) {
        if (ReplayLab.getInstance().isEditorOpen()) {
            ci.cancel();
        }
    }
}
