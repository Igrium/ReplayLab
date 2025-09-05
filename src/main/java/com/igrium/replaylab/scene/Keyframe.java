package com.igrium.replaylab.scene;

import lombok.Getter;
import lombok.Setter;

/**
 * A single keyframe in the timeline. Contains a time, scalar value, and any curve attributes.
 */
@Getter
@Setter
public class Keyframe {
    private float time;
    private double value;

    public Keyframe(float time, double value) {
        this.time = time;
        this.value = value;
    }

    public Keyframe(Keyframe other) {
        this(other.time, other.value);
    }

    public void copyFrom(Keyframe other) {
        this.time = other.time;
        this.value = other.value;
    }
}
