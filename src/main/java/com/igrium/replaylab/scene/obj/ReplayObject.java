package com.igrium.replaylab.scene.obj;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.util.GsonSerializationContext;
import com.igrium.replaylab.util.MutableDouble;
import imgui.ImColor;
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

    /**
     * A "return value" from the properties panel UI draw code. Used to trigger undo steps and scene updates.
     */
    public enum PropertiesPanelState {
        /**
         * Nothing changed in the properties panel.
         */
        NONE,
        /**
         * Something is being dragged or actively updated. Update the scene but don't create an undo step.
         */
        DRAGGING,
        /**
         * There was an update. Update the scene and create an undo step.
         */
        COMMIT,
        /**
         * There was an update that warrants the insertion of a keyframe if auto-key is enabled.
         */
        COMMIT_KEYFRAME;

        public boolean wantsUpdateScene() {
            return ordinal() >= 1;
        }

        public boolean wantsUndoStep() {
            return ordinal() >= 2;
        }

        public boolean wantsInsertKeyframe() {
            return ordinal() >= 3;
        }

        public static PropertiesPanelState max(PropertiesPanelState a, PropertiesPanelState b) {
            return PropertiesPanelState.values()[Math.max(a.ordinal(), b.ordinal())];
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
     * Called when the object is added to the scene
     */
    public void onAdded() {}

    /**
     * Called when the object is removed from the scene
     */
    public void onRemoved() {}

    /**
     * Called when the object is added to the scene for the first time.
     * Does not include undo, redo, or deserialization
     */
    public void onCreated() {}

    /**
     * Remap any property that references an object.
     * @param oldName Old name of the object.
     * @param newName New name of the object.
     */
    public void remapReferences(String oldName, String newName) {}

    /**
     * Called when the user has pressed the "insert keyframe" keybind with this object selected.
     * @param timestamp The current playback timestamp.
     * @return If the keyframe was created and an undo step should be created.
     */
    public boolean insertKey(int timestamp) {
        return false;
    }

    public final KeyChannel getOrCreateChannel(String name) {
        return channels.computeIfAbsent(name, n -> new KeyChannel());
    }

    public final void removeEmptyChannels() {
        channels.values().removeIf(ch -> ch.getKeyframes().isEmpty());
    }

    protected final void addProperty(String name, DoubleSupplier getter, DoubleConsumer setter) {
        getProperties().put(name, MutableDouble.of(getter, setter));
    }

    public final boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public final double getPropertyOrThrow(String propName) throws UnknownPropertyException {
        Double val = getProperty(propName);
        if (val != null) {
            return val;
        } else {
            throw new UnknownPropertyException(propName);
        }
    }

    public @Nullable Double getProperty(String propName) {
        MutableDouble prop = properties.get(propName);
        return prop != null ? prop.getDoubleValue() : null;
    }

    public final void setPropertyOrThrow(String propName, double value) throws UnknownPropertyException {
        if (!setProperty(propName, value)) {
            throw new UnknownPropertyException(propName);
        }
    }

    public boolean setProperty(String propName, double value) {
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
            if (!entry.getValue().getKeyframes().isEmpty()) {
                double val = entry.getValue().sample(timestamp);
                setProperty(entry.getKey(), val);
            }
        }
        apply(timestamp);
    }


    /**
     * Called during the ImGui render process to draw the object's configurable properties.
     * @return The state of the properties panel after drawing.
     */
    public PropertiesPanelState drawPropertiesPanel() {
        ImGui.text("This object has no editable properties.");
        return PropertiesPanelState.NONE;
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

    /**
     * Get this object's ID in the scene.
     * @return Object ID, or <code>null</code> if it's not currently in the scene.
     */
    public @Nullable String getId() {
        return getScene().getObjects().inverse().get(this);
    }

    public ReplayObject copy() {
        ReplayObject other = type.create(getScene());
        other.parse(this.save());
        return other;
    }

    /**
     * Return the color that the curve editor should use for a channel in this object.
     * @param chName Name of the channel.
     * @return ARGB packed int color.
     */
    public int getChannelColor(String chName) {
        // Get random hue from name hash
        int h = chName.hashCode();

        long z = (h & 0xFFFFFFFFL)
                + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);

        long top53 = z >>> 11; // keep the top 53 bits
        float hue = (float) (top53 / (double) (1L << 53));

        return ImColor.hsl(hue, .8f, .5f);
    }
}
