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

    @Getter
    private final List<KeyChannel> channels;

    protected KeyChannelCategory(List<KeyChannel> channels) {
        this.channels = channels;
    }

    public KeyChannelCategory() {
        this.channels = new ArrayList<>();
    }

    public KeyChannelCategory copy() {
        List<KeyChannel> copied = new ArrayList<>(channels.size());
        for (KeyChannel channel : channels) {
            copied.add(channel.copy());
        }
        return new KeyChannelCategory(copied);
    }
}
