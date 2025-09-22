package com.igrium.replaylab.scene.key;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * A single keyframe in the timeline. Contains a time, scalar value, and any curve attributes.
 */
@Getter @Setter
public class Keyframe implements Comparable<Keyframe> {
    public enum InterpolationMode {
        LINEAR
    }

    private int time;
    private double value;

    @NonNull
    @SerializedName("interp")
    private InterpolationMode interpolationMode = InterpolationMode.LINEAR;

    public Keyframe(int time, double value) {
        this.time = time;
        this.value = value;
    }

    public Keyframe(int time, double value, @NonNull InterpolationMode interpolationMode) {
        this.time = time;
        this.value = value;
        this.interpolationMode = interpolationMode;
    }

    public Keyframe(Keyframe other) {
        this(other.time, other.value, other.interpolationMode);
    }

    @Override
    public int compareTo(@NotNull Keyframe o) {
        return time - o.time;
    }
}
