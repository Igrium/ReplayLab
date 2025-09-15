package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.AnimationObject;
import com.igrium.replaylab.scene.obj.AnimationObjectType;

public class AddObjectOperator implements ReplayOperator {

    private final AnimationObjectType<?> type;
    private final String id;

    private AnimationObject object;

    public AddObjectOperator(AnimationObjectType<?> type, String id) {
        this.type = type;
        this.id = id;
    }

    @Override
    public boolean execute(ReplayScene scene) {
        object = type.create(scene);
        return scene.addObjectIfAbsent(id, object) == null;
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.removeObject(id);
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.addObject(id, object);
    }
}
