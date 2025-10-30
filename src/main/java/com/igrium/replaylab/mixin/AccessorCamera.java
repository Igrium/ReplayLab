package com.igrium.replaylab.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface AccessorCamera {
    @Accessor("cameraY")
    void setCameraY(float cameraY);

    @Accessor("lastCameraY")
    void setLastCameraY(float cameraY);
}
