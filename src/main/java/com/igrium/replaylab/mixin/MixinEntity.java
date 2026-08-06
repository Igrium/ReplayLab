package com.igrium.replaylab.mixin;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.ReplayObject3D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    private Level level;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    void lookDirectionChangeRoll(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!level.isClientSide()) {
            return;
        }
        var editor = EditorState.getInstance();
        if (editor != null && editor.isRollingCamera() && editor.isCameraView()
                && editor.getScene().getSceneCameraObject() instanceof ReplayObject3D cam) {

            cam.rotation().rotateZ((float) Math.toRadians(cursorDeltaX * .15f));
            ci.cancel();
        }
    }
}
