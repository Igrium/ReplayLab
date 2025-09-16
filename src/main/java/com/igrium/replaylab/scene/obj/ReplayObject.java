package com.igrium.replaylab.scene.obj;

import com.google.gson.*;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.util.GsonSerializationContext;
import imgui.ImGui;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * An object that can be animated within a replay
 */
public abstract class ReplayObject {

    @Getter
    private final ReplayObjectType<?> type;

    @Getter
    private final List<KeyChannel> channels = new ArrayList<>();

    public ReplayObject(ReplayObjectType<?> type) {
        this.type = type;
    }

    /**
     * Save this object's properties into a Json object.
     *
     * @param json    Json object to write to.
     * @param context Current json context.
     */
    protected void writeProperties(JsonObject json, JsonSerializationContext context) {
    }

    private final GsonSerializationContext gsonContext = new GsonSerializationContext(new Gson());

    /**
     * Overwrite this object's properties from values in a json object.
     *
     * @param json    Json to read from.
     * @param context Current json context
     */
    protected void readProperties(JsonObject json, JsonDeserializationContext context) {
    }

    public SerializedReplayObject save() {
        var props = new JsonObject();
        writeProperties(props, gsonContext);

        List<KeyChannel> channels = this.channels.stream().map(KeyChannel::copy).toList();

        return new SerializedReplayObject(type.getId(), channels, props);
    }

    public void parse(SerializedReplayObject obj) {
        readProperties(obj.getProps(), gsonContext);

        channels.clear();
        for (var c : obj.getChannels()) {
            channels.add(c.copy());
        }
    }

    /**
     * Compute the result of the animation curve(s) and apply it to the game.
     * @param timestamp Timestamp to sample.
     */
    public abstract void apply(int timestamp);

    /**
     * Called during the ImGui render process to draw the object's configurable properties.
     * @return If a property was updated this frame, triggering an undo step to be created.
     */
    public boolean drawPropertiesPanel() {
        ImGui.text("This object has no editable properties.");
        return false;
    }

    /**
     * Sample a specified channel at a given timestamp.
     * @param channel Channel index to sample.
     * @param timestamp Timestamp to sample at (ms)
     * @return The channels value, or 0 if the channel index is invalid.
     */
    public double sample(int channel, int timestamp) {
        if (channel < 0 || channel >= channels.size()) {
            return 0;
        } else {
            return channels.get(channel).sample(timestamp);
        }
    }
}
