package com.igrium.replaylab.camera;

/**
 * An entity that can provide a roll value for the camera.
 */
public interface RollProvider {

    /**
     * Get the desired camera roll.
     * @return Roll in degrees
     */
    float getRoll();
}
