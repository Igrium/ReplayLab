package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Remove all the given keyframes.
 */
public class RemoveKeyframesOperator implements ReplayOperator {

    private final ReplayScene.KeyReference[] keys;

    private Map<String, SerializedReplayObject> pre;
    private Map<String, SerializedReplayObject> post;


    public RemoveKeyframesOperator(ReplayScene.KeyReference... keys) {
        this.keys = keys.clone();
    }

    public RemoveKeyframesOperator(Collection<? extends ReplayScene.KeyReference> keys) {
        this.keys = keys.toArray(ReplayScene.KeyReference[]::new);
    }

    @Override
    public boolean execute(EditorState scene) {
        Map<String, Map<String, IntSet>> map = new HashMap<>();
        for (var keyRef : keys) {
            Map<String, IntSet> channels = map.computeIfAbsent(keyRef.object(), v -> new HashMap<>());
            IntSet channel = channels.computeIfAbsent(keyRef.channel(), v -> new IntArraySet());

            channel.add(keyRef.keyframe());
        }

        this.pre = new HashMap<>();
        this.post = new HashMap<>();

        boolean success = false;
        for (var objEntry : map.entrySet()) {
            ReplayObject obj = scene.getScene().getObject(objEntry.getKey());
            if (obj == null)
                continue;

            SerializedReplayObject pre = scene.getScene().getSavedObject(objEntry.getKey());
            if (pre == null) {
                pre = scene.getScene().saveObject(objEntry.getKey()); // Generally shouldn't happen
            }
            this.pre.put(objEntry.getKey(), pre);


            for (var chEntry : objEntry.getValue().entrySet()) {
                KeyChannel channel = obj.getChannels().get(chEntry.getKey());
                if (channel == null)
                    continue;

                int[] toRemove = chEntry.getValue().toIntArray();
                Arrays.sort(toRemove);

                for (int i = toRemove.length - 1; i >= 0; i--) {
                    int idx = toRemove[i];
                    if (0 <= idx && idx < channel.getKeyframes().size()) {
                        channel.getKeyframes().remove(idx);
                        success = true;
                    }
                }
            }

            SerializedReplayObject post = scene.getScene().saveObject(objEntry.getKey());
            this.post.put(objEntry.getKey(), post);
        }

        return success;
    }

    @Override
    public void undo(EditorState scene) {
        pre.forEach((name, serialized) -> {
            scene.getScene().setSavedObject(name, serialized);
            scene.getScene().revertObject(name);
        });
    }

    @Override
    public void redo(EditorState scene) {
        post.forEach((name, serialized) -> {
            scene.getScene().setSavedObject(name, serialized);
            scene.getScene().revertObject(name);
        });
    }
}
