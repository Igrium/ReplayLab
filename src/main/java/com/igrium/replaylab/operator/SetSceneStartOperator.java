package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.objs.ScenePropsObject;

public class SetSceneStartOperator implements ReplayOperator {

    private final int newStart;
    private final boolean shiftKeys;

    private int prevStart;

    public SetSceneStartOperator(int newStart, boolean shiftKeys) {
        this.newStart = Math.max(0, newStart);
        this.shiftKeys = shiftKeys;
    }

    @Override
    public boolean execute(EditorState editor) {
        ScenePropsObject sceneProps = editor.getScene().getSceneProps();
        prevStart = sceneProps.getStartTime();

        if (newStart == prevStart) return false;
        sceneProps.setStartTime(newStart);

        if (shiftKeys) {
            int delta = newStart - prevStart;
            for (var obj : editor.getScene().getObjects().values()) {
                for (var chan : obj.getChannels().values()) {
                    for (var key : chan.getKeyframes()) {
                        key.getCenter().x -= delta;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void undo(EditorState editor) {
        editor.getScene().getSceneProps().setStartTime(prevStart);

        if (shiftKeys) {
            int delta = newStart - prevStart;
            for (var obj : editor.getScene().getObjects().values()) {
                for (var chan : obj.getChannels().values()) {
                    for (var key : chan.getKeyframes()) {
                        key.getCenter().x += delta;
                    }
                }
            }
        }
    }

    @Override
    public void redo(EditorState editor) {
        editor.getScene().getSceneProps().setStartTime(newStart);

        if (shiftKeys) {
            int delta = newStart - prevStart;
            for (var obj : editor.getScene().getObjects().values()) {
                for (var chan : obj.getChannels().values()) {
                    for (var key : chan.getKeyframes()) {
                        key.getCenter().x -= delta;
                    }
                }
            }
        }
    }
}
