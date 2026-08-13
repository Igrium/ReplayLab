package com.igrium.replaylab.operator.object;

import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.types.ObjectCamera;
import com.igrium.replaylab.scene.ReplayScene;

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
        if (editor.getScene().addObjectIfAbsent(objectId, object) == null) {
            object.onCreated();
            editor.setActiveObject(objectId);

            var sceneProps = editor.getScene().getSceneProps();

            if ( object instanceof ObjectCamera
                    && ReplayLabConfig.getInstance().isAutoSetCamera()
                    && editor.getScene().getSceneProps().getCamera().isBlank()) {
                sceneProps.setCamera(objectId);
                // The scene is written from the serialized snapshot cache, so mutating the live
                // sceneProps isn't enough -- without this the assignment is lost on reload.
                editor.getScene().saveObject(ReplayScene.SCENE_PROPS);
                setCamera = true;
            }

            if (ReplayLabConfig.getInstance().isInspectOnCreate()) {
                editor.setWantOpenInspector(true);
            }

            return true;
        }
        return false;
    }

    @Override
    public void undo(EditorState editor) {
        if (setCamera) {
            editor.getScene().getSceneProps().setCamera("");
            editor.getScene().saveObject(ReplayScene.SCENE_PROPS);
        }
        editor.getScene().removeObject(objectId);
    }

    @Override
    public void redo(EditorState editor) {
        editor.getScene().addObject(objectId, object);
        editor.setActiveObject(objectId);
        if (setCamera) {
            editor.getScene().getSceneProps().setCamera(objectId);
            editor.getScene().saveObject(ReplayScene.SCENE_PROPS);
        }
    }
}
