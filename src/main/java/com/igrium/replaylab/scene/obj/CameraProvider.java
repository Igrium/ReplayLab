package com.igrium.replaylab.scene.obj;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A replay object which can provide a camera entity
 */
public interface CameraProvider {
    /**
     * Get the entity to spectate if this object is used as the scene camera.
     */
    @Nullable Entity getCameraEntity();
}
