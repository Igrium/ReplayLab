package com.igrium.replaylab.operator.keyframe;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.operator.object.MultiObjectOperator;
import com.igrium.replaylab.scene.key.InterpolationMode;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;

import java.util.Collection;
import java.util.Map;

public class SetInterpModeOperator extends MultiObjectOperator {
    private final Collection<KeyframeReference> keyframes;
    private final InterpolationMode newMode;

    public SetInterpModeOperator(InterpolationMode newMode, Collection<KeyframeReference> keyframes) {
        this.newMode = newMode;
        this.keyframes = keyframes;
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        return keyframes.stream().map(KeyframeReference::objectName).distinct().toList();
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {
        boolean success = false;
        for (var keyRef : keyframes) {
            Keyframe key = keyRef.get(objects);
            if (key != null && key.getInterpolationMode() != newMode) {
                key.setInterpolationMode(newMode);
                success = true;
            }
        }
        return success;
    }
}
