package com.igrium.replaylab.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A category of keyframe channels, each making up a single object (3D vector, etc)
 */
public class KeyChannelCategory {
    @NonNull
    @Getter
    @Setter
    private String name;

    @Getter
    private final List<KeyChannel> channels;

    protected KeyChannelCategory(@NonNull String name, List<KeyChannel> channels) {
        this.name = name;
        this.channels = channels;
    }

    public KeyChannelCategory(@NonNull String name) {
        this.channels = new ArrayList<>();
        this.name = name;
    }

    public KeyChannelCategory copy() {
        List<KeyChannel> copied = new ArrayList<>(channels.size());
        for (KeyChannel channel : channels) {
            copied.add(channel.copy());
        }
        return new KeyChannelCategory(name, copied);
    }
}
