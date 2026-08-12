package com.igrium.replaylab.operator.scene;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.EntityProvider;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.scene.ReplayScene;

public class SetSceneCameraOperator implements ReplayOperator {

    private String oldSceneCam;
    private final String newSceneCam;

    public SetSceneCameraOperator(String newSceneCam) {
        this.newSceneCam = newSceneCam;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        ReplayObject activeObj = editor.getScene().getObject(newSceneCam);
        if (!(activeObj instanceof EntityProvider)) return false;

        var sceneProps = editor.getScene().getSceneProps();
        oldSceneCam = sceneProps.getCameraObject();
        if (oldSceneCam.equals(newSceneCam)) return false;
        sceneProps.setCameraObject(newSceneCam);
        // The scene is written from the serialized snapshot cache, so the live sceneProps has to be
        // re-snapshotted or the assignment never reaches disk.
        editor.getScene().saveObject(ReplayScene.SCENE_PROPS);

        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        editor.getScene().getSceneProps().setCameraObject(oldSceneCam);
        editor.getScene().saveObject(ReplayScene.SCENE_PROPS);
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        editor.getScene().getSceneProps().setCameraObject(newSceneCam);
        editor.getScene().saveObject(ReplayScene.SCENE_PROPS);
    }
}
