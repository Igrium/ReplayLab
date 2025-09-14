package com.igrium.replaylab.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single "channel" of keyframes. A given channel always contain a single curve of scalar values.
 * If you need to tie multiple values together (like a vector), use a {@link KeyChannelCategory}
 */
public class KeyChannel {

    @NonNull
    @Getter
    @Setter
    private String name;

    @Getter
    private final List<Keyframe> keys;

    protected KeyChannel(@NonNull String name, List<Keyframe> keyframes) {
        this.name = name;
        this.keys = keyframes;
    }

    public KeyChannel(@NonNull String name) {
        this.keys = new ArrayList<>();
        this.name = name;
    }

    /**
     * Sample the curve at a given timestamp.
     * @param timestamp Timestamp to sample at.
     * @return The scalar value of the curve at that time.
     */
    public double sample(int timestamp) {
        return 0; // TODO: implement
    }

    public KeyChannel copy() {
        List<Keyframe> copied = new ArrayList<>(keys.size());
        for (Keyframe key : keys) {
            copied.add(new Keyframe(key));
        }
        return new KeyChannel(name, copied);
    }
}
