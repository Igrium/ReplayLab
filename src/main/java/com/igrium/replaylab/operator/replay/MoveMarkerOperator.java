package com.igrium.replaylab.operator.replay;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.replaymod.replaystudio.data.Marker;

public class MoveMarkerOperator implements ReplayOperator {

    private final Marker marker;
    private final int oldTime;
    private final int newTime;

    public MoveMarkerOperator(Marker marker, int oldTime, int newTime) {
        this.marker = marker;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        if (oldTime == newTime) return false;

        marker.setTime(newTime);
        editor.saveMarkers();
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        marker.setTime(oldTime);
        editor.saveMarkers();
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        marker.setTime(newTime);
        editor.saveMarkers();
    }

    @Override
    public boolean wantsSampleCurves() {
        return false;
    }
}
