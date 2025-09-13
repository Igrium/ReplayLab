package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;

public interface UndoStep {
    void undo(ReplayScene scene);
    void redo(ReplayScene scene);
}
