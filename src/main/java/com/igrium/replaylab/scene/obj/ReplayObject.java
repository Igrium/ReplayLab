package com.igrium.replaylab.scene.obj;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.util.GsonSerializationContext;
import com.igrium.replaylab.util.MutableDouble;
import imgui.ImGui;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * An object that can be animated within a replay
 */
public abstract class ReplayObject {

    /**
     * Thrown when a non-existent property is attempted to be accessed.
     */
    public static class UnknownPropertyException extends IllegalArgumentException {
        @Getter
        private final String propName;

        public UnknownPropertyException(String propName) {
            super("Unknown property name: " + propName);
            this.propName = propName;
        }
    }

    @Getter
    private final ReplayObjectType<?> type;

    @Getter
    private final ReplayScene scene;

    /**
     * All properties exposed to animation.
     * @apiNote Does <em>not</em> automatically serialize. Make sure to implement <code>readJson</code> and <code>writeJson</code>
     */
    @Getter
    private final Map<String, MutableDouble> properties = new HashMap<>();

    /**
     * All animation channels in the object. Not all properties have a channel.
     */
    @Getter
    private final Map<String, KeyChannel> channels = new HashMap<>();

    public ReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        this.type = type;
        this.scene = scene;
    }

    /**
     * Called when the user has pressed the "insert keyframe" keybind with this object selected.
     */
    public void insertKey() {
    }

    protected final void addProperty(String name, DoubleSupplier getter, DoubleConsumer setter) {
        getProperties().put(name, MutableDouble.of(getter, setter));
    }

    public final boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public double getProperty(String propName) throws UnknownPropertyException {
        MutableDouble prop = properties.get(propName);
        if (prop == null) {
            throw new UnknownPropertyException(propName);
        } else {
            return prop.getDoubleValue();
        }
    }

    public @Nullable Double tryGetProperty(String propName) {
        MutableDouble prop = properties.get(propName);
        return prop != null ? prop.getDoubleValue() : null;
    }

    public void setProperty(String propName, double value) throws UnknownPropertyException {
        MutableDouble prop = properties.get(propName);
        if (prop == null) {
            throw new UnknownPropertyException(propName);
        } else {
            prop.setDoubleValue(value);
        }
    }

    public boolean trySetProperty(String propName, double value) {
        MutableDouble prop = properties.get(propName);
        if (prop != null) {
            prop.setDoubleValue(value);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Save this object's attributes into a Json object.
     *
     * @param json    Json object to write to.
     * @param context Current json context.
     */
    protected abstract void writeJson(JsonObject json, JsonSerializationContext context);
    /**
     * Overwrite this object's attributes from values in a json object.
     *
     * @param json    Json to read from.
     * @param context Current json context
     */
    protected abstract void readJson(JsonObject json, JsonDeserializationContext context);

    /**
     * Apply all current properties to the game.
     * @param timestamp Timestamp to apply. Rarely used as properties will already have been updated.
     */
    public abstract void apply(int timestamp);

    /**
     * Sample all channels and apply properties to the game.
     * @param timestamp Timestamp to sample.
     */
    public void sampleAndApply(int timestamp) {
        for (var entry : channels.entrySet()) {
            double val = entry.getValue().sample(timestamp);
            trySetProperty(entry.getKey(), val);
        }
        apply(timestamp);
    }

    /**
     * Called during the ImGui render process to draw the object's configurable properties.
     * @return If a property was updated this frame, triggering an undo step to be created.
     */
    public boolean drawPropertiesPanel() {
        ImGui.text("This object has no editable properties.");
        return false;
    }

    private final GsonSerializationContext gsonContext = new GsonSerializationContext(new Gson());

    /**
     * Serialize this replay object for use in the undo stack (or disk).
     * @return Serialized replay object.
     */
    public SerializedReplayObject save() {
        JsonObject attributes = new JsonObject();
        writeJson(attributes, gsonContext);

        var channels = new HashMap<String, KeyChannel>(getChannels().size());
        for (var entry : getChannels().entrySet()) {
            channels.put(entry.getKey(), entry.getValue().copy());
        }

        return new SerializedReplayObject(type.getId(), ImmutableMap.copyOf(channels), attributes);
    }

    /**
     * Clear and parse a serialized replay object, applying its attributes to this.
     * @param serialized Serialized replay object from the undo stack (or disk).
     */
    public void parse(SerializedReplayObject serialized) {
        readJson(serialized.getAttributes(), gsonContext);

        channels.clear();
        for (var entry : serialized.getChannels().entrySet()) {
            channels.put(entry.getKey(), entry.getValue().copy());
        }
    }

    public ReplayObject copy() {
        ReplayObject other = type.create(getScene());
        other.parse(this.save());
        return other;
    }
}
