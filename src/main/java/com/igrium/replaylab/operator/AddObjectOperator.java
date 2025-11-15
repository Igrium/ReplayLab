package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;

/**
 * Add a new object to the scene, calling <code>onCreated</code> in the process.
 */
public class AddObjectOperator implements ReplayOperator {

    private final String objectId;
    private final ReplayObject object;

    public AddObjectOperator(String objectId, ReplayObject object) {
        this.objectId = objectId;
        this.object = object;
    }

    @Override
    public boolean execute(EditorState scene) {
        if (scene.getScene().addObjectIfAbsent(objectId, object)) {
            object.onCreated();
            return true;
        }
        return false;
    }

    @Override
    public void undo(EditorState scene) {
        scene.getScene().removeObject(objectId);
    }

    @Override
    public void redo(EditorState scene) {
        scene.getScene().addObject(objectId, object);
    }
}
