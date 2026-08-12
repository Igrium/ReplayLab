package com.igrium.replaylab.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface AccessorCamera {
    @Accessor("eyeHeight")
    void setEyeHeight(float cameraY);

    @Accessor("eyeHeightOld")
    void setEyeHeightOld(float cameraY);
}
