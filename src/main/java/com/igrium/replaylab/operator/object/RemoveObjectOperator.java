package com.igrium.replaylab.operator.object;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.object.ReplayObject;

public class RemoveObjectOperator implements ReplayOperator {

    private final String objectId;
    private ReplayObject obj;

    public RemoveObjectOperator(String objectId) {
        this.objectId = objectId;
    }

    @Override
    public boolean execute(EditorState scene) {
        if (objectId.equals(ReplayScene.SCENE_PROPS)) {
            // Don't delete the scene props
            return false;
        }

        obj = scene.getScene().removeObject(objectId);
        return obj != null;
    }

    @Override
    public void undo(EditorState scene) {
        scene.getScene().addObject(objectId, obj);
    }

    @Override
    public void redo(EditorState scene) {
        scene.getScene().removeObject(objectId);
    }
}
