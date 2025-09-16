package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonObject;
import com.igrium.replaylab.scene.key.KeyChannel;
import lombok.Getter;

import java.util.List;

/**
 * An immutable, serialized form of a replay object designed for use in undo/redo
 */
public class SerializedReplayObject {
    @Getter
    private final String type;
    @Getter
    private final List<KeyChannel> channels;
    @Getter
    private final JsonObject props;

    public SerializedReplayObject(String type, List<KeyChannel> channels, JsonObject props) {
        this.type = type;
        this.channels = channels;
        this.props = props;
    }
}
