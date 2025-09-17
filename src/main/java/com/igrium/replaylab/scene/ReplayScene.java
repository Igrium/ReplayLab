package com.igrium.replaylab.scene;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjects;
import com.igrium.replaylab.scene.obj.objs.ScenePropsObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Keeps track of all the runtime stuff regarding a scene.
 * @apiNote <b>Warning:</b> Calling most of these functions on their own can corrupt the undo/redo stack.
 * Prefer using operators.
 */
public class ReplayScene {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayScene");

    public static final String SCENE_PROPS = "scene";

    /**
     *
     * Contains a "reference" to a particular keyframe within the manifest.
     * If the manifest gets re-initialized for any reason, this reference should remain.
     *
     * @param object   Object name
     * @param channel  Channel name within the category.
     * @param keyframe Keyframe index within the channel.
     */
    public record KeyReference(String object, String channel, int keyframe) {
    }

    /**
     * All the objects in this scene.
     */
    private final BiMap<String, ReplayObject> objects = HashBiMap.create();
    private final BiMap<String, ReplayObject> objectsUnmod = Maps.unmodifiableBiMap(objects);

    /**
     * A map of serialized versions of each object. Used for undo/redo.
     */
    private final BiMap<String, SerializedReplayObject> savedObjects = HashBiMap.create();
    private final BiMap<String, SerializedReplayObject> savedObjectsUnmod = Maps.unmodifiableBiMap(savedObjects);

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

    public @Nullable Keyframe getKeyframe(String object, String channel, int keyframe) {
        if (keyframe < 0) return null;

        ReplayObject obj = getObject(object);
        if (obj == null) return null;

        KeyChannel ch = obj.getChannels().get(channel);
        if (ch == null || keyframe >= ch.getKeys().size()) return null;

        return ch.getKeys().get(keyframe);
    }

    public @Nullable Keyframe getKeyframe(KeyReference ref) {
        return getKeyframe(ref.object(), ref.channel(), ref.keyframe());
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
        SerializedReplayObject s = obj.save();
        savedObjects.put(id, s);
    }

    private void onRemoveObject(String id, ReplayObject obj) {
        savedObjects.remove(id);
    }

    /**
     * Return a map of all the serialized objects in this scene.
     * @return Unmodifiable view of serialized objects.
     * @apiNote Will not include uncommitted changes.
     */
    public BiMap<String, SerializedReplayObject> getSavedObjects() {
        return savedObjectsUnmod;
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
     * Attempt to execute an operator.
     * @param operator Operator to execute.
     * @return <code>true</code> if the operation was successful and the operator was added to the undo stack.
     */
    public boolean applyOperator(ReplayOperator operator) {
        boolean result;
        try {
            result = operator.execute(this);
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
     * Sample and apply all animated values from the scene into the game
     * @param timestamp Timestamp to apply.
     * @apiNote Does not apply game packets, only directly animated values like camera moves.
     */
    public void applyToGame(int timestamp) {
        for (var obj : getObjects().values()) {
            obj.apply(timestamp);
        }
    }

    /**
     * Clear the scene and re-create it from the serialized form of all its objects.
     * @param serialized Serialized objects.
     */
    public void readSerializedObjects(Map<? extends String, ? extends SerializedReplayObject> serialized) {
        savedObjects.clear();
        objects.clear();

        savedObjects.putAll(serialized);

        for (var entry : savedObjects.entrySet()) {
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
