package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;

public class AddObjectOperator implements ReplayOperator {

    private final String objectId;
    private final ReplayObject object;

    public AddObjectOperator(String objectId, ReplayObject object) {
        this.objectId = objectId;
        this.object = object;
    }

    @Override
    public boolean execute(ReplayScene scene) {
        return scene.addObjectIfAbsent(objectId, object);
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.removeObject(objectId);
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.addObject(objectId, object);
    }
}
