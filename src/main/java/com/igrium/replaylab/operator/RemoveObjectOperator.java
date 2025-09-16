package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.objs.ReplayObject;

public class RemoveObjectOperator implements ReplayOperator {

    private final String objectId;
    private ReplayObject obj;

    public RemoveObjectOperator(String objectId) {
        this.objectId = objectId;
    }

    @Override
    public boolean execute(ReplayScene scene) {
        if (objectId.equals(ReplayScene.SCENE_PROPS)) {
            // Don't delete the scene props
            return false;
        }

        obj = scene.removeObject(objectId);
        return obj != null;
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.addObject(objectId, obj);
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.removeObject(objectId);
    }
}
