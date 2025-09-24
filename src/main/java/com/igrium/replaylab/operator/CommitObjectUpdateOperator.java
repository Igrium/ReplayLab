package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;

import java.util.*;

/**
 * Called after an object has been updated from the UI to add said operation to the undo stack.
 */
public class CommitObjectUpdateOperator implements ReplayOperator {
    private final String[] ids;

    private Map<String, SerializedReplayObject> pre;
    private Map<String, SerializedReplayObject> post;

    public CommitObjectUpdateOperator(Collection<? extends String> ids) {
        this.ids = ids.toArray(String[]::new);
    }

    public CommitObjectUpdateOperator(String... ids) {
        this.ids = ids.clone();
    }

    @Override
    public boolean execute(ReplayScene scene) {
        pre = new HashMap<>();
        for (var id : ids) {
            pre.put(id, Objects.requireNonNull(scene.getSavedObject(id)));
        }
        post = new HashMap<>();
        for (var id : ids) {
            post.put(id, Objects.requireNonNull(scene.saveObject(id)));
        }
        return true;
    }

    @Override
    public void undo(ReplayScene scene) {
        for (var entry : pre.entrySet()) {
            scene.setSavedObject(entry.getKey(), entry.getValue());
            scene.revertObject(entry.getKey());
        }
    }

    @Override
    public void redo(ReplayScene scene) {
        for (var entry : post.entrySet()) {
            scene.setSavedObject(entry.getKey(), entry.getValue());
            scene.revertObject(entry.getKey());
        }
    }
}
