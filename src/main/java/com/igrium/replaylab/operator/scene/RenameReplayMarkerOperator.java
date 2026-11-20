package com.igrium.replaylab.operator.scene;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.replaymod.replaystudio.data.Marker;
import org.jspecify.annotations.Nullable;

public class RenameReplayMarkerOperator implements ReplayOperator {

    /**
     * I don't really like that this is being referenced by object reference,
     * but replay mod doesn't index them in any meaningful way
     */
    private final Marker marker;

    private final @Nullable String newName;
    private @Nullable String oldName;

    public RenameReplayMarkerOperator(Marker marker, @Nullable String newName) {
        this.marker = marker;
        this.newName = newName;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        oldName = marker.getName();
        marker.setName(newName);
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        marker.setName(oldName);
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        marker.setName(newName);
    }
}
