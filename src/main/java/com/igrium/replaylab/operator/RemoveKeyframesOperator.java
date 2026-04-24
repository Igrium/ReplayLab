package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.editor.KeySelectionSet.KeyframeReference;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.ReplayObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.*;

public class RemoveKeyframesOperator extends MultiObjectOperator {

    private final KeyframeReference[] keys;

    public RemoveKeyframesOperator(KeyframeReference... keys) {
        this.keys = keys.clone();
    }

    public RemoveKeyframesOperator(Collection<? extends KeyframeReference> keys) {
        this.keys = keys.toArray(KeyframeReference[]::new);
    }

    @Override
    protected Collection<? extends String> getTargetObjects(EditorState editor) {
        Set<String> names = new HashSet<>();
        for (var key : keys) {
            names.add(key.objectName());
        }
        return names;
    }

    @Override
    protected boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception {
        // Index all keys
        Map<String, Map<String, IntSet>> map = new HashMap<>();
        for (var keyRef : keys) {
            var channels = map.computeIfAbsent(keyRef.objectName(), k -> new HashMap<>());
            var channel = channels.computeIfAbsent(keyRef.channelName(), v -> new IntArraySet());
            channel.add(keyRef.keyIndex());
        }

        boolean success = false;
        for (var objEntry : map.entrySet()) {
            ReplayObject obj = objects.get(objEntry.getKey());
            if (obj == null) continue;

            for (var chEntry : objEntry.getValue().entrySet()) {
                KeyChannel chan = obj.getChannels().get(chEntry.getKey());
                if (chan == null) continue;

                int[] toRemove = chEntry.getValue().toIntArray();
                Arrays.sort(toRemove);

                // Remove backwards because we're doing it based on index
                for (int i = toRemove.length - 1; i >= 0; i--) {
                    int idx = toRemove[i];
                    if (0 <= idx && idx < chan.getKeyframes().size()) {
                        chan.getKeyframes().remove(idx);
                        success = true;
                    }
                }
            }
        }
        return success;
    }

}
