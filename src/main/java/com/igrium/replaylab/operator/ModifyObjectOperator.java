package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;


public class ModifyObjectOperator implements ReplayOperator {

    private final String objectId;

    public ModifyObjectOperator(String objectId) {
        this.objectId = objectId;
    }

    private SerializedReplayObject pre;
    private SerializedReplayObject post;

    @Override
    public boolean execute(ReplayScene scene) {
        pre = scene.getSavedObject(objectId);
        if (pre == null) {
            throw new IllegalStateException("Object has no pre-saved state.");
        }
        post = scene.saveObject(objectId);
        return true;
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
