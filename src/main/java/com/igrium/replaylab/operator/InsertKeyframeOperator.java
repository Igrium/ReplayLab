package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;

public class InsertKeyframeOperator extends SimpleObjectOperator {
    private final int timestamp;

    public InsertKeyframeOperator(String objectId, int timestamp) {
        super(objectId);
        this.timestamp = timestamp;
    }

    @Override
    public boolean execute(EditorState scene, ReplayObject object) {
        return object.insertKey(timestamp);
    }
}
