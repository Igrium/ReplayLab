package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.EditorScene;
import com.igrium.replaylab.scene.KeyframeManifest;

public record ApplyKeyManifestOperator(KeyframeManifest prev, KeyframeManifest curr) implements UndoStep {
    @Override
    public void undo(EditorScene scene) {
        scene.setInternalKeyManifest(prev);
        scene.resetKeyManifest();
    }

    @Override
    public void redo(EditorScene scene) {
        scene.setInternalKeyManifest(curr);
        scene.resetKeyManifest();
    }
}
