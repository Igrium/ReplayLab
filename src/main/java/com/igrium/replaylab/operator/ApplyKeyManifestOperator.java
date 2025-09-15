package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.key.KeyframeManifest;
import com.igrium.replaylab.scene.ReplayScene;

/**
 * Called after the user is finished messing with keyframes
 */
public class ApplyKeyManifestOperator implements ReplayOperator {
    // Effectively immutable from here on out; can just reuse instances.
    private KeyframeManifest pre;
    private KeyframeManifest post;

    @Override
    public boolean execute(ReplayScene scene) {
        KeyframeManifest updated = scene.getKeyManifest().copy();
        pre = scene.getInternalKeyManifest();
        post = updated;
        scene.setInternalKeyManifest(updated);
        return true;
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.setInternalKeyManifest(pre);
        scene.resetKeyManifest();
    }

    @Override
    public void redo(ReplayScene scene){
        scene.setInternalKeyManifest(post);
        scene.resetKeyManifest();
    }
}
