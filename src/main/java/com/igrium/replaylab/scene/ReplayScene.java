package com.igrium.replaylab.scene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.CameraProvider;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import com.igrium.replaylab.util.NameUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps track of all the runtime stuff regarding a scene.
 * @apiNote <b>Warning:</b> Calling most of these functions on their own can corrupt the undo/redo stack.
 * Prefer using operators.
 */
public class ReplayScene {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayScene");

    public static final String SCENE_PROPS = "scene";

    /**
     * Contains a "reference" to a particular keyframe within the manifest.
     * If the manifest gets re-initialized for any reason, this reference should remain.
     *
     * @param object   Object name
     * @param channel  Channel name within the category.
     * @param keyframe Keyframe index within the channel.
     */
    @Deprecated
    public record KeyReference(String object, String channel, int keyframe) {
    }

    /**
     * Contains a "reference" to a particular keyframe handle within the scene.
     *
     * @param object   Object name
     * @param channel  Channel name
     * @param keyframe Keyframe index within the channel.
     * @param handle   Handle index of the keyframe. 0 = center, 1 = left, 2 = right
     */
    @Deprecated
    public record KeyHandleReference(String object, String channel, int keyframe, int handle) {
        public KeyHandleReference(KeyReference keyRef, int handle) {
            this(keyRef.object(), keyRef.channel(), keyRef.keyframe(), handle);
        }

        public KeyReference toKeyReference() {
            return new KeyReference(object, channel, keyframe);
        }
    }

    /**
     * All the objects in this scene.
     */
    private final BiMap<String, ReplayObject> objects = HashBiMap.create();
    private final BiMap<String, ReplayObject> objectsUnmod = Maps.unmodifiableBiMap(objects);

    /**
     * A map of serialized versions of each object. Used for undo/redo.
     */
    @Getter
    private final SerializedObjectHolder savedObjects = new SerializedObjectHolder();
//    private final BiMap<String, SerializedReplayObject> savedObjects = Maps.synchronizedBiMap(HashBiMap.create());
//    private final BiMap<String, SerializedReplayObject> savedObjectsUnmod = Maps.unmodifiableBiMap(savedObjects);

    private final Deque<ReplayOperator> undoStack = new ArrayDeque<>();
    private final Deque<ReplayOperator> redoStack = new ArrayDeque<>();

    @Setter @Nullable
    private Consumer<? super Exception> exceptionCallback;

//    public ReplayScene() {
//        addObject("sceneProps", ReplayObjectType.SCENE_PROPS.create());
//    }

    public ScenePropsObject getSceneProps() {
        ReplayObject obj = getObject(SCENE_PROPS);
        ScenePropsObject sceneProps;
        if (obj instanceof ScenePropsObject) {
            sceneProps = (ScenePropsObject) obj;
        } else {
            LOGGER.info("No Scene object found. Creating.");
            sceneProps = ReplayObjects.SCENE_PROPS.create(this);
            addObject(SCENE_PROPS, sceneProps);
        }
        return sceneProps;
    }

    public int getLength() {
        return getSceneProps().getLength();
    }

    /**
     * Get the time in the replay where the scene starts.
     * @return Global replay start time (ms)
     */
    public int getStartTime() {
        return getSceneProps().getStartTime();
    }

    /**
     * Convert a local timestamp to a global replay time suitable for the relay mod.
     * @param sceneTimestamp Scene time in ms.
     * @return Global replay time in ms.
     */
    public int sceneToReplayTime(int sceneTimestamp) {
        // TODO: Update this to handle time dilation
        return sceneTimestamp + getStartTime();
    }

    public float getFps() {
        return getSceneProps().getFps();
    }

    public @Nullable KeyChannel getChannel(String object, String channel) {
        ReplayObject obj = getObject(object);
        return obj != null ? obj.getChannels().get(channel) : null;
    }

    public @Nullable KeyChannel getChannel(KeyReference ref) {
        return getChannel(ref.object(), ref.channel());
    }

    public @Nullable Keyframe getKeyframe(String object, String channel, int keyframe) {
        if (keyframe < 0) return null;

        ReplayObject obj = getObject(object);
        if (obj == null) return null;

        KeyChannel ch = obj.getChannels().get(channel);
        if (ch == null || keyframe >= ch.getKeyframes().size()) return null;

        return ch.getKeyframes().get(keyframe);
    }

    public @Nullable Keyframe getKeyframe(KeyReference ref) {
        return getKeyframe(ref.object(), ref.channel(), ref.keyframe());
    }

    public @Nullable Keyframe getKeyframe(KeyHandleReference ref) {
        return getKeyframe(ref.object(), ref.channel(), ref.keyframe());
    }

    public @Nullable ReplayObject getSceneCameraObject(int timestamp) {
        String objName = getSceneProps().getCameraObject();
        if (objName == null)
            return null;

        return getObject(objName);
    }

    /**
     * Get the entity responsible for providing the camera view on a given frame.
     * @param timestamp Timestamp to use.
     * @return The scene camera entity. if there is any at that timestamp.
     */
    public @Nullable Entity getSceneCamera(int timestamp) {
        ReplayObject obj = getSceneCameraObject(timestamp);
        if (obj instanceof CameraProvider prov) {
            return prov.getCameraEntity();
        } else {
            return null;
        }
    }

    public void spectateCamera(int timestamp) {
        Entity cam = getSceneCamera(timestamp);
        if (cam != null) {
            MinecraftClient.getInstance().setCameraEntity(cam);
        }
    }

    /**
     * Get a map of all the replay objects in this scene.
     * @return Unmodifiable view of all objects.
     */
    public BiMap<String, ReplayObject> getObjects() {
        return objectsUnmod;
    }

    /**
     * Get the animation object belonging to a specific ID.
     * @param id ID to search for.
     * @return The object, or <code>null</code> if no object by that ID exists.
     */
    public @Nullable ReplayObject getObject(String id) {
        return objects.get(id);
    }

    /**
     * Add a replay object to this scene, replacing any old ones.
     *
     * @param id  ID to assign the new object.
     * @param obj Object to add.
     * @return The previous object that belonged to that ID, if any.
     */
    public @Nullable ReplayObject addObject(String id, ReplayObject obj) {
        if (obj.getScene() != this) {
            throw new IllegalArgumentException("Object belongs to the wrong scene.");
        }
        var prev = objects.put(id, obj);
        if (prev != null) {
            onRemoveObject(id, prev);
        }
        onAddObject(id, obj);
        return prev;
    }

    /**
     * Add a replay object to this scene if there isn't already one with its ID.
     *
     * @param id  ID to assign the new object.
     * @param obj Object to add.
     * @return <code>true</code> if the object was added; <code>false</code> if there was a naming conflict.
     */
    public boolean addObjectIfAbsent(String id, ReplayObject obj) {
        if (obj.getScene() != this) {
            throw new IllegalArgumentException("Object belongs to the wrong scene.");
        }
        if (objects.putIfAbsent(id, obj) == null) {
            onAddObject(id, obj);
            return true;
        }
        return false;
    }


    /**
     * Remove a replay object from the scene.
     * @param id ID of the object to remove.
     * @return The object that was removed, if any.
     */
    public @Nullable ReplayObject removeObject(String id) {
        var obj = objects.remove(id);
        if (obj != null) {
            onRemoveObject(id, obj);
        }
        return obj;
    }

    private void onAddObject(String id, ReplayObject obj) {
        obj.onAdded();
        SerializedReplayObject s = obj.save();
        savedObjects.put(id, s);
    }

    private void onRemoveObject(String id, ReplayObject obj) {
        obj.onRemoved();
        savedObjects.remove(id);
    }

    /**
     * Attempt to rename an object.
     *
     * @param oldName The original object name.
     * @param newName The new name to assign it.
     * @return <code>true</code> if the object was renamed successfully.
     * <code>false</code> if the object could not be found or there was a conflict with the new name.
     */
    public boolean renameObject(String oldName, @NonNull String newName) {
        if (!objects.containsKey(oldName) || objects.containsKey(newName)) {
            return false;
        }
        var object = objects.remove(oldName);
        var saved = savedObjects.remove(oldName);

        objects.put(newName, object);
        savedObjects.put(newName, saved);

        for (var obj : objects.values()) {
            obj.remapReferences(oldName, newName);
        }

        return true;
    }

    public @Nullable SerializedReplayObject getSavedObject(String id) {
        return savedObjects.get(id);
    }


    public void setSavedObject(String id, SerializedReplayObject obj) {
        savedObjects.put(id, obj);
    }

    /**
     * Save an object into the saved object cache. Used when an undo step is created.
     * @param id ID of object to save.
     * @return The new serialized data. <code>null</code> if the object could not be found.
     */
    public @Nullable SerializedReplayObject saveObject(String id) {
        ReplayObject obj = getObject(id);
        if (obj == null) {
            LOGGER.warn("Unable to commit object {} because it cannot be found.", id);
            return null;
        }

        SerializedReplayObject serialized = obj.save();
        savedObjects.put(id, serialized);
        return serialized;
    }

    /**
     * Revert an object to the version in the saved object cache.
     * @param id ID of the object to revert.
     */
    public void revertObject(String id) {
        ReplayObject obj = getObject(id);
        if (obj == null) {
            LOGGER.warn("Unable to revert object {} because it cannot be found.", id);
            return;
        }

        var serialized = savedObjects.get(id);
        if (serialized == null) {
            LOGGER.warn("No serialized form of {} found.", id);
            return;
        }

        obj.parse(serialized);
    }

    /**
     * Make a given object name unique so it doesn't conflict with anything else in the scene.
     * @param original Original object name.
     * @return The unique object name.
     */
    public String makeNameUnique(String original) {
        return NameUtils.makeNameUnique(original, objects::containsKey);
    }

    /**
     * Attempt to execute an operator.
     * @param operator Operator to execute.
     * @return <code>true</code> if the operation was successful and the operator was added to the undo stack.
     */
    public boolean applyOperator(EditorState editorState, ReplayOperator operator) {
        boolean result;
        try {
            result = operator.execute(editorState);
        } catch (Exception e) {
            LOGGER.error("Error executing operator {}: ", operator.getClass().getSimpleName(), e);
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
    public boolean undo(EditorState editorState) {
        if (undoStack.isEmpty()) return false;

        ReplayOperator op = undoStack.pop();
        try {
            op.undo(editorState);
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
    public boolean redo(EditorState editorState) {
        if (redoStack.isEmpty()) return false;

        ReplayOperator op = redoStack.pop();
        try {
            op.redo(editorState);
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
     * Sample and apply all animated values from the scene into the game
     *
     * @param timestamp Timestamp to apply.
     * @apiNote Does not apply game packets, only directly animated values like camera moves.
     */
    public void applyToGame(int timestamp) {
        applyToGame(e -> true, timestamp);
    }

    /**
     * Apply all animated values from the scene into the game.
     *
     * @param shouldSample <code>true</code> if a given object should be sampled as it's applied.
     * @param timestamp    Timestamp to apply.
     * @apiNote Does not apply game packets, only directly animated values like camera moves.
     */
    public void applyToGame(Predicate<? super ReplayObject> shouldSample, int timestamp) {
        for (var obj : getObjects().values()) {
            if (shouldSample.test(obj)) {
                obj.sampleAndApply(timestamp);
            } else {
                obj.apply(timestamp);
            }
        }
    }

    /**
     * Clear the scene and re-create it from the serialized form of all its objects.
     * @param serialized Serialized objects.
     */
    public void readSerializedObjects(Map<? extends String, ? extends SerializedReplayObject> serialized) {
        objects.clear();
        savedObjects.replaceContents(serialized);

        for (var entry : serialized.entrySet()) {
            try {
                objects.put(entry.getKey(), ReplayObjects.deserialize(entry.getValue(), this));
            } catch (Exception e) {
                LOGGER.error("Error deserializing replay object {}: ", entry.getKey(), e);
                if (exceptionCallback != null) {
                    exceptionCallback.accept(e);
                }
            }
        }
    }
}
