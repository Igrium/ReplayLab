package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonObject;
import com.igrium.replaylab.anim.KeyChannel;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * An immutable, serialized form of a replay object designed for use in undo/redo
 */
public final class SerializedReplayObject {

    @Getter
    private final String type;

    @Getter
    private final Map<String, KeyChannel> channels;

    @Getter
    private final JsonObject attributes;

    public SerializedReplayObject(String type, Map<String, KeyChannel> channels, JsonObject attributes) {
        this.type = type;
        this.channels = channels;
        this.attributes = attributes;
    }
}
