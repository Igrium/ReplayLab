package com.igrium.replaylab.mixin;

import com.igrium.replaylab.camera.RollProvider;
import com.igrium.replaylab.editor.EditorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.beans.Encoder;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    private World world;

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    void lookDirectionChangeRoll(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!world.isClient) {
            return;
        }
        var editor = EditorState.getInstance();
        var client = MinecraftClient.getInstance();
        if (editor != null && editor.isRollingCamera() && client.getCameraEntity() instanceof RollProvider cam) {
            float r = (float) (cursorDeltaX * .15f);
            cam.setRoll(cam.getRoll() + r);
            ci.cancel();
        }
    }
}
