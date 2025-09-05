package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.EditorScene;

public interface UndoStep {
    void undo(EditorScene scene);
    void redo(EditorScene scene);
}
