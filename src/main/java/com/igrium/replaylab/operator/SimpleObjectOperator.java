package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An operator that applies a modification to an object, handling the undo and redo automatically
 */
public abstract class SimpleObjectOperator implements ReplayOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/SimpleObjectOperator");

    private final String objectId;

    private SerializedReplayObject pre;
    private SerializedReplayObject post;

    protected SimpleObjectOperator(String objectId) {
        this.objectId = objectId;
    }

    public abstract boolean execute(EditorState scene, ReplayObject object) throws Exception;

    @Override
    public boolean execute(EditorState scene) throws Exception {
        ReplayObject obj = scene.getScene().getObject(objectId);
        if (obj == null) {
            LOGGER.warn("No object found with id: {}", objectId);
            return false;
        }

        pre = scene.getScene().getSavedObject(objectId);
        if (pre == null) {
            LOGGER.warn("No saved object for {}", objectId);
            pre = scene.getScene().saveObject(objectId);
        }
        if (execute(scene, obj)) {
            post = scene.getScene().saveObject(objectId);
            return true;
        }
        return false;
    }

    @Override
    public void undo(EditorState scene) {
        scene.getScene().setSavedObject(objectId, pre);
        scene.getScene().revertObject(objectId);
    }

    @Override
    public void redo(EditorState scene) {
        scene.getScene().setSavedObject(objectId, post);
        scene.getScene().revertObject(objectId);
    }
}
