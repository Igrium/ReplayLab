package com.igrium.replaylab.operator.keyframe;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.anim.Keyframe;

public class SetKeyPosOperator implements ReplayOperator {
    private final KeySelectionSet.KeyframeReference keyRef;
    private final double time;
    private final double value;

    private double prevTime;
    private double prevValue;

    public SetKeyPosOperator(KeySelectionSet.KeyframeReference keyRef, double time, double value) {
        this.keyRef = keyRef;
        this.time = time;
        this.value = value;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        Keyframe key = keyRef.get(editor.getScene().getObjects());
        if (key == null) return false;

        prevTime = key.getTime();
        prevValue = key.getValue();
        key.setTime(time);
        key.setValue(value);
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        Keyframe key = keyRef.get(editor.getScene().getObjects());
        if (key == null) return;

        key.setTime(prevTime);
        key.setValue(prevValue);
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        Keyframe key = keyRef.get(editor.getScene().getObjects());
        if (key == null) return;

        key.setTime(time);
        key.setValue(value);
    }
}
