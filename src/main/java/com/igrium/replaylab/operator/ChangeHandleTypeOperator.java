package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.KeyHandleReference;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeHandleTypeOperator implements ReplayOperator {

    private final List<KeyHandleReference> handles;
    private final HandleType handleType;

    private Map<KeyHandleReference, HandleType> prevHandles;

    public ChangeHandleTypeOperator(Collection<? extends KeyHandleReference> handles, HandleType handleType) {
        this.handles = List.copyOf(handles);
        this.handleType = handleType;
    }
    
    private HandleType getHandleType(Keyframe key, int handleIdx) {
        return switch (handleIdx) {
            case 0, 1 -> key.getHandleAType();
            case 2 -> key.getHandleBType();
            default -> throw new IllegalStateException("Unexpected value: " + handleIdx);
        };
    }

    private void setHandleType(Keyframe key, int handleIdx, HandleType handleType) {
        switch (handleIdx) {
            case 0 -> {
                key.setHandleAType(handleType);
                key.setHandleBType(handleType);
            }
            case 1 -> key.setHandleAType(handleType);
            case 2 -> key.setHandleBType(handleType);
        }
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        boolean success = false;
        prevHandles = new HashMap<>(handles.size());
        for (KeyHandleReference handle : handles) {
            Keyframe key = handle.keyRef().get(editor.getScene().getObjects());
            if (key == null)
                 continue;

            prevHandles.put(handle, getHandleType(key, handle.handleIndex()));
            setHandleType(key, handle.handleIndex(), handleType);
            success = true;
        }
        return success;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        for (var entry : prevHandles.entrySet()) {
            Keyframe key = entry.getKey().keyRef().get(editor.getScene().getObjects());
            if (key == null)
                continue;

            setHandleType(key, entry.getKey().handleIndex(), entry.getValue());
        }
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        for (KeyHandleReference handle : handles) {
            Keyframe key = handle.keyRef().get(editor.getScene().getObjects());
            if (key == null)
                continue;

            setHandleType(key, handle.handleIndex(), handleType);
        }
    }

}
