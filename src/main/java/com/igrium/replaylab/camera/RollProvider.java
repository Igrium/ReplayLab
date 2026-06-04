package com.igrium.replaylab.camera;

/**
 * An entity that can hold a roll in addition to pitch and yaw.
 * <p>
 * <em>Not</em> used for rendering; only for piloting cameras.
 * @see RotationProvider
 */
public interface RollProvider {
    /**
     * Get the current roll in degrees.
     */
    float getRoll();

    /**
     * Set the roll
     * @param roll New roll in degrees
     */
    void setRoll(float roll);
}
