package com.igrium.replaylab.scene.key;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
 */
public class KeyChannel {

    @Getter
    private final List<Keyframe> keys;

    @Getter @Setter @NonNull
    private String name = "";

    protected KeyChannel(List<Keyframe> keyframes) {
        this.keys = keyframes;
    }

    public KeyChannel(@NonNull String name) {
        this.name = name;
        this.keys = new ArrayList<>();
    }

    /**
     * Sample the curve at a given timestamp.
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        return 0; // TODO: implement
    }

    /**
     * Make a deep copy of this channel.
     */
    public KeyChannel copy() {
        List<Keyframe> copied = new ArrayList<>(keys.size());
        for (Keyframe key : keys) {
            copied.add(new Keyframe(key));
        }
        return new KeyChannel(copied);
    }
}


