package com.igrium.replaylab.operator.scene;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.obj.ScenePropsObject;

public class SetSceneLengthOperator implements ReplayOperator {

    private final int length;

    private int prevLength;

    public SetSceneLengthOperator(int length) {
        this.length = Math.max(length, 0);
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        ScenePropsObject sceneProps = editor.getScene().getSceneProps();
        prevLength = sceneProps.getLength();
        if (prevLength == length) return false;

        sceneProps.setLength(length);
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        editor.getScene().getSceneProps().setLength(prevLength);
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        editor.getScene().getSceneProps().setLength(length);
    }
}
