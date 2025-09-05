package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.EditorScene;
import com.igrium.replaylab.scene.KeyframeManifest;

public record ApplyKeyManifestOperator(KeyframeManifest pre, KeyframeManifest post) implements UndoStep {
    @Override
    public void undo(EditorScene scene) {
        scene.setInternalKeyManifest(pre);
        scene.resetKeyManifest();
    }

    @Override
    public void redo(EditorScene scene) {
        scene.setInternalKeyManifest(post);
        scene.resetKeyManifest();
    }
}
