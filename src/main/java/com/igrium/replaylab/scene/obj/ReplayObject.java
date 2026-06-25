package com.igrium.replaylab.scene.obj;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.util.GsonSerializationContext;
import com.igrium.replaylab.util.MutableDouble;
import imgui.ImColor;
import imgui.ImGui;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

import java.util.*;
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

    /**
     * The value of each property last time it was sampled.
     */
    @Getter
    private final Object2DoubleMap<String> sampledValues = new Object2DoubleArrayMap<>();

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

    public final boolean isSceneCamera() {
        String id = getId();
        return id != null && id.equals(scene.getSceneProps().getCameraObject());
    }

    /**
     * Remap any property that references an object.
     * @param oldName Old name of the object.
     * @param newName New name of the object.
     */
    public void remapReferences(String oldName, String newName) {}

    /**
     * <code>true</code> if this object should be hidden from the outliner
     */
    public boolean isHiddenInOutliner() {
        return false;
    }

    /**
     * Called when the user has pressed the "insert keyframe" keybind with this object selected.
     * @param timestamp The current playback timestamp.
     * @return If the keyframe was created and an undo step should be created.
     */
    @Deprecated
    public boolean insertKey(int timestamp) {
        return false;
    }



    public final @Nullable KeyChannel getChannel(String name) {
        return channels.get(name);
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
        MutableDouble prop = properties.get(propName);
        if (prop == null) throw new UnknownPropertyException(propName);
        return prop.getDoubleValue();
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
     * Sample all keyframed properties in this object, writing them into the object's internal memory.
     * @param timestamp Timestamp to sample.
     * @apiNote Does NOT apply properties to the game! Use {@link #sampleAndApply}.
     */
    public void sample(int timestamp) {
        for (var entry : channels.entrySet()) {
            if (!entry.getValue().getKeyframes().isEmpty()) {
                double val = entry.getValue().sample(timestamp);
                sampledValues.put(entry.getKey(), val);
                setProperty(entry.getKey(), val);
            }
        }
    }

    /**
     * Sample all channels and apply properties to the game.
     * @param timestamp Timestamp to sample.
     */
    public final void sampleAndApply(int timestamp) {
        sample(timestamp);
        apply(timestamp);
    }

    /**
     * Called every frame in the editor to draw this object's gizmos.
     *
     * @param editor           The current editor state.
     * @param cameraPos        World-space position of the camera.
     * @param viewMatrix       View matrix (rotation, etc.) of the camera. Does not include position.
     * @param projectionMatrix Projection matrix of the camera.
     * @param hideUI           Don't draw any visual gizmos (some objects may still need to update things while UI disabled)
     * @return {@link ObjectEditState}
     */
    public int drawGizmos(EditorState editor, Vector3dc cameraPos,
                                           Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        // Default no-op
        return ObjectEditState.NONE;
    }

    /**
     * Called during the ImGui render process to draw the object's configurable properties.
     * @return {@link ObjectEditState}
     */
    public int drawPropertiesPanel(EditorState editor) {
        ImGui.text("This object has no editable properties.");
        return ObjectEditState.NONE;
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


    /**
     * Insert a keyframe at the playhead.
     *
     * @param editor    Current editor state.
     * @param timestamp The scene timestamp to add the keyframe at
     * @param pos       If a position keyframe is requested
     * @param rot       If a rotation keyframe is requested
     * @param scale     If a scale keyframe is requested
     * @return The new keyframes.
     */
    public Collection<? extends KeySelectionSet.KeyframeReference> insertKeyframe(EditorState editor, int timestamp, boolean pos, boolean rot, boolean scale) {
        // Default NOOP
        return Collections.emptyList();
    }
}
