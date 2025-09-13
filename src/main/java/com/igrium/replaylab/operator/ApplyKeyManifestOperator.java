package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.KeyframeManifest;

public record ApplyKeyManifestOperator(KeyframeManifest pre, KeyframeManifest post) implements UndoStep {
    @Override
    public void undo(ReplayScene scene) {
        scene.setInternalKeyManifest(pre);
        scene.resetKeyManifest();
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.setInternalKeyManifest(post);
        scene.resetKeyManifest();
    }
}
