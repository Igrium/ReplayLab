package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonObject;
import com.igrium.replaylab.anim.KeyChannel;
import com.igrium.replaylab.anim.constraint.ConstraintContainer.ConstraintEntry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable, serialized form of a replay object designed for use in undo/redo.
 * The main reason this is used instead of a pure json object is for optimization with keyframes
 */
// TODO: does this actually need to be not a json object?
@SuppressWarnings("ClassCanBeRecord") // Values are mutable
public final class SerializedReplayObject {

    @Getter
    private final String type;

    @Getter
    private final Map<String, KeyChannel> channels;

    @Getter
    private final JsonObject attributes;

    @Getter
    private final List<ConstraintEntry> constraints;

    public SerializedReplayObject(String type, Map<String, KeyChannel> channels, JsonObject attributes, List<ConstraintEntry> constraints) {
        this.type = type;
        this.channels = channels;
        this.attributes = attributes;
        this.constraints = constraints;
    }

    // Empty constructor for gson
    private SerializedReplayObject() {
        this.type = "";
        this.channels = new HashMap<>();
        this.attributes = new JsonObject();
        this.constraints = new ArrayList<>();
    }
}
