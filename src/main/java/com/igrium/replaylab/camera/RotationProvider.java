package com.igrium.replaylab.camera;

import org.joml.Quaternionf;

/**
 * An entity that provides its rotation as a quaternion.
 * If used as the camera, this is used instead of pitch/yaw
 */
public interface RotationProvider {
    /**
     * Get the entity's rotation as a quaternion.
     * @param dest Place the result here.
     * @return <code>dest</code>
     */
    Quaternionf getRotationQuat(Quaternionf dest);
}
