package com.igrium.replaylab.operator.scene;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.ObjectSceneProps;

public class SetSceneStartOperator implements ReplayOperator {

    private final int newStart;
    private final boolean shiftKeys;

    private int prevStart;
    private int prevLength;

    /**
     * Create a new instance
     *
     * @param newStart  The new start time in milliseconds
     * @param shiftKeys If set, shift the keys and playhead to counteract the scene move so they're in the same place
     *                 in the replay
     */
    public SetSceneStartOperator(int newStart, boolean shiftKeys) {
        this.newStart = Math.max(0, newStart);
        this.shiftKeys = shiftKeys;
    }

    @Override
    public boolean execute(EditorState editor) {
        ObjectSceneProps sceneProps = editor.getScene().getSceneProps();
        prevStart = sceneProps.getStartTime();
        prevLength = sceneProps.getLength(); // Avoid undo contamination if setLength clamps

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
            editor.setPlayhead(editor.getPlayhead() - delta);
            sceneProps.setLength(sceneProps.getLength() - delta);
        }

        return true;
    }

    @Override
    public void undo(EditorState editor) {
        ObjectSceneProps sceneProps = editor.getScene().getSceneProps();
        sceneProps.setStartTime(prevStart);

        if (shiftKeys) {
            int delta = newStart - prevStart;
            for (var obj : editor.getScene().getObjects().values()) {
                for (var chan : obj.getChannels().values()) {
                    for (var key : chan.getKeyframes()) {
                        key.getCenter().x += delta;
                    }
                }
            }
            editor.setPlayhead(editor.getPlayhead() + delta);
            sceneProps.setLength(prevLength);
        }
    }

    @Override
    public void redo(EditorState editor) {
        ObjectSceneProps sceneProps = editor.getScene().getSceneProps();
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
            editor.setPlayhead(editor.getPlayhead() - delta);
            sceneProps.setLength(sceneProps.getLength() - delta);
        }
    }
}
