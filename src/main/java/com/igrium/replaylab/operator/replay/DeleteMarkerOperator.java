package com.igrium.replaylab.operator.replay;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.replaymod.replaystudio.data.Marker;

public class DeleteMarkerOperator implements ReplayOperator {

    private final Marker marker;

    public DeleteMarkerOperator(Marker marker) {
        this.marker = marker;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        boolean success = editor.getReplayMarkers().remove(this.marker);
        if (success) {
            editor.saveMarkers();
        }
        return success;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        editor.getReplayMarkers().add(this.marker);
        editor.saveMarkers();
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        editor.getReplayMarkers().remove(this.marker);
        editor.saveMarkers();
    }

    @Override
    public boolean wantsSampleCurves() {
        return false;
    }
}
