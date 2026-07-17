package com.igrium.replaylab.scene.obj;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.igrium.replaylab.anim.constraint.Constraint;
import com.igrium.replaylab.anim.constraint.ConstraintContainer;
import com.igrium.replaylab.anim.constraint.ObjectAccessor;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.anim.KeyChannel;
import json.GsonSerializationContext;
import imgui.ImColor;
import imgui.ImGui;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

import java.io.Serializable;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * An object that can be animated within a replay
 */
public abstract class ReplayObject implements PropertyHolder {

    /// === NESTED TYPES ===

    /// === FIELDS ===

    @Getter
    private final ReplayObjectType<?> type;

    @Getter
    private final ReplayScene scene;

    /**
     * All properties exposed to animation.
     *
     * @apiNote Does <em>not</em> automatically serialize. Make sure to implement <code>readJson</code> and
     * <code>writeJson</code>
     */
    @Getter
    private final Map<String, PropertyHolder.Property> properties = new HashMap<>();

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

    @Getter
    private final ConstraintContainer constraints = new ConstraintContainer(this);

    private final GsonSerializationContext gsonContext = new GsonSerializationContext(new Gson());

    /// === CONSTRUCTOR ===

    public ReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        this.type = type;
        this.scene = scene;
    }

    /// === LIFECYCLE ===

    /**
     * Called when the object is added to the scene
     */
    public void onAdded() {
    }

    /**
     * Called when the object is removed from the scene
     */
    public void onRemoved() {
    }

    /**
     * Called when the object is added to the scene for the first time.
     * Does not include undo, redo, or deserialization
     */
    public void onCreated() {
    }

    /// === PROPERTIES ===

    protected final void addProperty(String name, DoubleSupplier getter, DoubleConsumer setter) {
        addProperty(name, new PropertyHolder.Property(getter, setter));
    }

    protected final void addProperty(String name, PropertyHolder.Property property) {
        if (name.contains(".")) {
            throw new IllegalArgumentException("Property names may not contain '.'");
        }
        getProperties().put(name, property);
    }

    public @Nullable PropertyHolder.Property getPropertyRef(String propName) {
        String[] split = propName.split("\\.", 2);

        if (split.length > 1) {
            Constraint<?> c = getConstraints().get(split[0]);
            if (c == null) return null;
            return c.getPropertyRef(split[1]);
        } else {
            return properties.get(propName);
        }
    }


    /// === CHANNELS ===

    public final @Nullable KeyChannel getChannel(String name) {
        return channels.get(name);
    }

    public final KeyChannel getOrCreateChannel(String name) {
        return channels.computeIfAbsent(name, n -> new KeyChannel());
    }

    public final void removeEmptyChannels() {
        channels.values().removeIf(ch -> ch.getKeyframes().isEmpty());
    }

    /**
     * Return the color that the curve editor should use for a channel in this object.
     *
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

    /// === SAMPLING ===

    /**
     * Evaluate and apply all the constraints in this object
     * @param timestamp Timestamp to evaluate at
     * @param accessor Object accessor to use for evaluation
     */
    public void evaluateConstraints(int timestamp, ObjectAccessor accessor) {
        getConstraints().evaluate(timestamp, accessor);
    }

    /**
     * Sample all keyframed properties in this object, writing them into the object's internal memory.
     *
     * @param timestamp Timestamp to sample.
     * @apiNote Does NOT apply properties to the game! Use {@link #sampleAndApply}.
     */
    public void sample(int timestamp) {
        for (var entry : channels.entrySet()) {
            if (!entry.getValue().getKeyframes().isEmpty()) {
                double val = entry.getValue().sample(timestamp, true);
                sampledValues.put(entry.getKey(), val);
                setProperty(entry.getKey(), val);
            }
        }
    }



    /**
     * Sample all channels and apply properties to the game.
     *
     * @param timestamp Timestamp to sample.
     */
    public final void sampleAndApply(int timestamp) {
        sample(timestamp);
        apply(timestamp);
    }

    /**
     * Apply all current properties to the game.
     *
     * @param timestamp Timestamp to apply. Rarely used as properties will already have been updated.
     */
    public abstract void apply(int timestamp);

    /// === SERIALIZATION ===

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
     * Serialize this replay object for use in the undo stack (or disk).
     *
     * @return Serialized replay object.
     */
    public SerializedReplayObject save() {
        JsonObject attributes = new JsonObject();
        writeJson(attributes, gsonContext);

        var channels = new HashMap<String, KeyChannel>(getChannels().size());
        for (var entry : getChannels().entrySet()) {
            channels.put(entry.getKey(), entry.getValue().copy());
        }

        return new SerializedReplayObject(type.getId(), ImmutableMap.copyOf(channels),
                attributes, ImmutableList.copyOf(constraints.save(gsonContext)));
    }

    /**
     * Clear and parse a serialized replay object, applying its attributes to this.
     *
     * @param serialized Serialized replay object from the undo stack (or disk).
     */
    public void parse(SerializedReplayObject serialized) {
        readJson(serialized.getAttributes(), gsonContext);

        channels.clear();
        for (var entry : serialized.getChannels().entrySet()) {
            channels.put(entry.getKey(), entry.getValue().copy());
        }

        constraints.parse(serialized.getConstraints(), gsonContext);
    }

    public ReplayObject copy() {
        ReplayObject other = type.create(getScene());
        other.parse(this.save());
        return other;
    }

    /// === EDITOR / UI ===

    /**
     * Called every frame in the editor to draw this object's gizmos.
     *
     * @param editor           The current editor state.
     * @param cameraPos        World-space position of the camera.
     * @param viewMatrix       View matrix (rotation, etc.) of the camera. Does not include position.
     * @param projectionMatrix Projection matrix of the camera.
     * @param hideUI           Don't draw any visual gizmos (some objects may still need to update things while UI
     *                         disabled)
     * @return {@link ObjectEditState}
     */
    public int drawGizmos(EditorState editor, Vector3dc cameraPos,
                          Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        // Default no-op
        return ObjectEditState.NONE;
    }

    /**
     * Called during the ImGui render process to draw the object's configurable properties.
     *
     * @return {@link ObjectEditState}
     */
    public int drawPropertiesPanel(EditorState editor) {
        ImGui.text("This object has no editable properties.");
        return ObjectEditState.NONE;
    }

    /**
     * Called when the "insert keyframe" keybinding is pressed
     *
     * @param editor    Current editor state.
     * @param timestamp The scene timestamp to add the keyframe at
     * @param pos       If a position keyframe is requested
     * @param rot       If a rotation keyframe is requested
     * @param scale     If a scale keyframe is requested
     * @return The new keyframes.
     */
    public Collection<? extends KeySelectionSet.KeyframeReference> insertKeyframe(EditorState editor, int timestamp,
                                                                                  boolean pos, boolean rot,
                                                                                  boolean scale) {
        // Default NOOP
        return Collections.emptyList();
    }

    /// === SCENE / IDENTITY ===

    public final boolean isSceneCamera() {
        String id = getId();
        return id != null && id.equals(scene.getSceneProps().getCameraObject());
    }

    /**
     * Remap any property that references an object.
     *
     * @param oldName Old name of the object.
     * @param newName New name of the object.
     */
    public void remapReferences(String oldName, String newName) {
    }

    /**
     * Get this object's ID in the scene.
     *
     * @return Object ID, or <code>null</code> if it's not currently in the scene.
     */
    public @Nullable String getId() {
        return getScene().getObjects().inverse().get(this);
    }

    public String getDisplayName() {
        String id = getId();
        return id != null ? id : "";
    }

    /// === UTILITY ===

    private static <K, T, R> Map<K, List<R>> transformMapValues(Map<K, List<T>> sourceMap,
                                                                Function<T, R> valueTransformer) {

        Map<K, List<R>> transformedMap = new HashMap<>();

        for (var entry : sourceMap.entrySet()) {
            K key = entry.getKey();
            List<T> originalList = entry.getValue();

            List<R> transformedList = new ArrayList<>(originalList.size());
            for (T originalItem : originalList) {
                transformedList.add(valueTransformer.apply(originalItem));
            }

            transformedMap.put(key, transformedList);
        }

        return transformedMap;
    }
}