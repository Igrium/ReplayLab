package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.objs.CameraObject;

/**
 * Add a new object to the scene, calling <code>onCreated</code> in the process.
 */
public class AddObjectOperator implements ReplayOperator {

    private final String objectId;
    private final ReplayObject object;
    private boolean setCamera;

    public AddObjectOperator(String objectId, ReplayObject object) {
        this.objectId = objectId;
        this.object = object;
    }

    @Override
    public boolean execute(EditorState editor) {
        if (editor.getScene().addObjectIfAbsent(objectId, object)) {
            object.onCreated();

            var sceneProps = editor.getScene().getSceneProps();

            if (object instanceof CameraObject && editor.getScene().getSceneProps().getCameraObject().isBlank()) {
                sceneProps.setCameraObject(objectId);
                setCamera = true;
            }

            return true;
        }
        return false;
    }

    @Override
    public void undo(EditorState editor) {
        if (setCamera) {
            editor.getScene().getSceneProps().setCameraObject("");
        }
        editor.getScene().removeObject(objectId);
    }

    @Override
    public void redo(EditorState editor) {
        editor.getScene().addObject(objectId, object);
        if (setCamera) {
            editor.getScene().getSceneProps().setCameraObject(objectId);
        }
    }
}
