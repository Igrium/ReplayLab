package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModifyObjectsOperator implements ReplayOperator {
    private final List<String> ids;

    private Map<String, SerializedReplayObject> pre;
    private Map<String, SerializedReplayObject> post;

    public ModifyObjectsOperator(Collection<? extends String> ids) {
        this.ids = List.copyOf(ids);
    }


    @Override
    public boolean execute(ReplayScene scene) {
        for (var id : ids) {
            pre.put(id, Objects.requireNonNull(scene.getSavedObject(id)));
        }
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
