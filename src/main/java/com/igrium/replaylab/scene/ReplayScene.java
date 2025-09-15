package com.igrium.replaylab.scene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.KeyChannelCategory;
import com.igrium.replaylab.scene.key.KeyframeManifest;
import com.igrium.replaylab.scene.obj.AnimationObject;
import com.igrium.replaylab.scene.obj.AnimationObjectType;
import com.igrium.replaylab.operator.ApplyKeyManifestOperator;
import com.igrium.replaylab.operator.ReplayOperator;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Keeps track of all the runtime stuff regarding a scene.
 */
@JsonAdapter(ReplaySceneSerializer.class)
public final class ReplayScene {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayScene");

    /**
     * The keyframe manifest that gets saved into the file, used in undo/redo, etc.
     * <b>CONSIDERED EFFECTIVELY IMMUTABLE</b>
     */
    @Getter @Setter @NonNull
    private KeyframeManifest internalKeyManifest = new KeyframeManifest();

    /**
     * The version of the keyframe manifest that gets edited, displayed, etc.
     * before getting saved to the undo stack.
     */
    @Getter @Setter @NonNull
    private KeyframeManifest keyManifest = new KeyframeManifest();

    @Setter @Nullable
    private transient Consumer<? super Exception> exceptionCallback;

    private final BiMap<String, AnimationObject> objects = HashBiMap.create();
    private final BiMap<String, AnimationObject> objectsUnmod = Maps.unmodifiableBiMap(objects);

    private final Deque<ReplayOperator> undoStack = new ArrayDeque<>();
    private final Deque<ReplayOperator> redoStack = new ArrayDeque<>();

    /**
     * The global start position of the rendered scene in ticks.
     */
    @Getter
    private int startTick;

    public void setStartTick(int startTick) {
        if (startTick < 0) {
            throw new IllegalArgumentException("Start tick may not be negative.");
        }
        this.startTick = startTick;
    }

    /**
     * The length of the scene in ticks.
     */
    @Getter
    private int length = 10000;

    public void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length may not be negative.");
        }
        this.length = length;
    }

    /**
     * Convert a time local to the scene into a global replay time suitable for the replay mod.
     *
     * @param sceneTime Scene time in Minecraft ticks.
     * @return Replay time in milliseconds.
     */
    public int sceneToReplayTime(int sceneTime) {
        // TODO: Update this to handle time dilation
        return sceneTime + startTick;
    }

    /**
     * Apply all animated values in the scene to the game. Does <em>not</em> update actual game time;
     * simply updates all manually-animated values.
     * @param time Timestamp to apply (in ms).
     */
    public void applyToGame(int time) {
        // TODO: actually implement this
    }

    /**
     * Get a map of all the animation objects in this scene.
     * @return Unmodifiable view of all objects.
     */
    public BiMap<String, AnimationObject> getObjects() {
        return objectsUnmod;
    }

    /**
     * Get the animation object belonging to a specific ID.
     * @param id ID to search for.
     * @return The object, or <code>null</code> if no object by that ID exists.
     */
    public @Nullable AnimationObject getObject(String id) {
        return objects.get(id);
    }

    /**
     * Add an animation object to this scene.
     *
     * @param id     ID to assign the new object.
     * @param object Object to add.
     * @return The previous object that belonged to that ID, if any.
     * @throws IllegalArgumentException If the object belongs to the wrong scene.
     */
    public @Nullable AnimationObject addObject(String id, AnimationObject object) throws IllegalArgumentException {
        if (object.getScene() != this) {
            throw new IllegalArgumentException("Object belongs to the wrong scene!");
        }
        var prev = objects.put(id, object);
        if (prev != null) {
            onRemoveObject(id, prev);
        }
        onAddObject(id, object);
        return prev;
    }

    /**
     * Add an animation object to this scene if there is none already existing with that ID.
     *
     * @param id     ID to assign the new object.
     * @param object Object to add.
     * @return The current object that belonged to that ID, if any.
     * @throws IllegalArgumentException If the object belongs to the wrong scene.
     */
    public @Nullable AnimationObject addObjectIfAbsent(String id, AnimationObject object) throws IllegalArgumentException {
        if (object.getScene() != this) {
            throw new IllegalArgumentException("Object belongs to the wrong scene!");
        }
        var prev = objects.putIfAbsent(id, object);
        if (prev == null) {
            onAddObject(id, object);
        }
        return prev;
    }

    private void onAddObject(String id, AnimationObject obj) {
        KeyChannelCategory cat = new KeyChannelCategory();
        for (var name : obj.listChannelNames()) {
            cat.getChannels().add(new KeyChannel(name));
        }
        keyManifest.getCategories().put(id, cat);
        commitKeyframeUpdates();
    }

    /**
     * Remove an animation object from the scene.
     *
     * @param id ID of the object to remove.
     * @return The object that had that ID, if any.
     */
    public @Nullable AnimationObject removeObject(String id) {
        var obj = objects.remove(id);
        if (obj != null) {
            onRemoveObject(id, obj);
        }
        return obj;
    }

    private void onRemoveObject(String id, AnimationObject obj) {
        keyManifest.getCategories().remove(id);
        commitKeyframeUpdates();
    }

    /**
     * Restore the working keyframe manifest to a copy of the internal one
     */
    public void resetKeyManifest() {
        keyManifest = internalKeyManifest.copy();
    }

    /**
     * Apply the working keyframe manifest into the core keyframe manifest,
     * creating an undo step in the process.
     */
    public void commitKeyframeUpdates() {
        applyOperator(new ApplyKeyManifestOperator());

    }

    /**
     * Attempt to execute an operator.
     * @param operator Operator to execute.
     * @return <code>true</code> if the operation was successful and the operator was added to the undo stack.
     */
    public boolean applyOperator(ReplayOperator operator) {
        boolean result;
        try {
            result = operator.execute(this);
        } catch (Exception e) {
            LOGGER.error("Error applying operator: ", e);
            // We consider the undo/redo stack corrupted.
            undoStack.clear();
            redoStack.clear();
            if (exceptionCallback != null) {
                exceptionCallback.accept(e);
            }
            return false;
        }
        if (result) {
            undoStack.push(operator);
            redoStack.clear();
        }
        return result;
    }

    /**
     * Undo the previous operation.
     * @return <code>true</code> if there was an operator to undo and it undid successfully.
     */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;

        ReplayOperator op = undoStack.pop();
        try {
            op.undo(this);
        } catch (Exception e) {
            LOGGER.error("Error undoing operator: ", e);
            undoStack.clear();
            redoStack.clear();
            if (exceptionCallback != null) {
                exceptionCallback.accept(e);
            }
            return false;
        }
        redoStack.push(op);
        return true;
    }

    /**
     * Redo the previous operation.
     * @return <code>true</code> if there was an operator to redo and it redid successfully.
     */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;

        ReplayOperator op = redoStack.pop();
        try {
            op.redo(this);
        } catch (Exception e) {
            LOGGER.error("Error redoing operator: ", e);
            undoStack.clear();
            redoStack.clear();
            if (exceptionCallback != null) {
                exceptionCallback.accept(e);
            }
            return false;
        }
        undoStack.push(op);
        return true;
    }

    /**
     * Parse a serialized replay scene and apply its values.
     * @param json Json to parse.
     */
    public void readJson(JsonObject json, JsonDeserializationContext gson) {
        undoStack.clear();
        redoStack.clear();

        JsonElement keyObj = json.get("keys");
        setInternalKeyManifest(gson.deserialize(keyObj, KeyframeManifest.class));

        resetKeyManifest();

        objects.clear();

        for (var entry : json.getAsJsonObject("objects").entrySet()) {
            try {
                JsonObject jsonObj = entry.getValue().getAsJsonObject();
                AnimationObject object = AnimationObjectType.fromJson(jsonObj, this);
                objects.put(entry.getKey(), object);
            } catch (Exception e) {
                LOGGER.error("Error parsing animation object {}: ", entry.getKey(), e);
            }
        }

        // TODO: scene global data
    }

    public void writeJson(JsonObject json, JsonSerializationContext gson) {

        JsonElement keyObj = gson.serialize(getInternalKeyManifest());

        json.add("keys", keyObj);

        JsonObject objectSet = new JsonObject();
        for (var entry : objectsUnmod.entrySet()) {
            JsonObject jsonObj = AnimationObjectType.toJson(entry.getValue(), new JsonObject());
            objectSet.add(entry.getKey(), jsonObj);
        }

        json.add("objects", objectSet);
    }
}

class ReplaySceneSerializer implements JsonSerializer<ReplayScene>, JsonDeserializer<ReplayScene> {

    @Override
    public ReplayScene deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        ReplayScene scene = new ReplayScene();
        scene.readJson(json.getAsJsonObject(), context);
        return scene;
    }

    @Override
    public JsonElement serialize(ReplayScene src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        src.writeJson(obj, context);
        return obj;
    }
}