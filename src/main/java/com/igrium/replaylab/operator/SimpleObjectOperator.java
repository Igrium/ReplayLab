package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import com.igrium.replaylab.ui.ReplayLabEditorState;
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

    public abstract boolean execute(ReplayScene scene, ReplayObject object) throws Exception;

    @Override
    public boolean execute(ReplayScene scene) throws Exception {
        ReplayObject obj = scene.getObject(objectId);
        if (obj == null) {
            LOGGER.warn("No object found with id: {}", objectId);
            return false;
        }

        pre = scene.saveObject(objectId);
        if (execute(scene, obj)) {
            post = scene.saveObject(objectId);
            return true;
        }
        return false;
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.setSavedObject(objectId, pre);
        scene.revertObject(objectId);
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.setSavedObject(objectId, post);
        scene.revertObject(objectId);
    }
}
