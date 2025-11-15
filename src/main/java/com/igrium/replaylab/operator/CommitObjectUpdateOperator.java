package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import com.igrium.replaylab.util.ArrayUtils;

import java.util.*;

/**
 * Called after an object has been updated from the UI to add said operation to the undo stack.
 * Also sorts all channels in the object.
 */
public class CommitObjectUpdateOperator implements ReplayOperator {
    private final String[] ids;

    private Map<String, SerializedReplayObject> pre;
    private Map<String, SerializedReplayObject> post;

    private final Map<String, Map<String, int[]>> preSelectionMapping;
    private final Map<String, Map<String, int[]>> postSelectionMapping;

    public CommitObjectUpdateOperator(Collection<? extends String> ids) {
        this.ids = ids.toArray(String[]::new);
        preSelectionMapping = new HashMap<>(this.ids.length);
        postSelectionMapping = new HashMap<>(this.ids.length);
    }

    public CommitObjectUpdateOperator(String... ids) {
        this.ids = ids.clone();
        preSelectionMapping = new HashMap<>(this.ids.length);
        postSelectionMapping = new HashMap<>(this.ids.length);
    }

    @Override
    public boolean execute(EditorState editor) {
        pre = new HashMap<>();
        for (var id : ids) {
            pre.put(id, Objects.requireNonNull(editor.getScene().getSavedObject(id)));

            ReplayObject obj = editor.getScene().getObject(id);

            // Remap selected keyframes
            if (obj != null) {
                for (var chEntry : obj.getChannels().entrySet()) {
                    int[] preMapping = chEntry.getValue().sortKeys();
                    int[] postMapping = ArrayUtils.invert(preMapping);

                    preSelectionMapping.computeIfAbsent(id, i -> new HashMap<>()).put(chEntry.getKey(), preMapping);
                    postSelectionMapping.computeIfAbsent(id, i -> new HashMap<>()).put(chEntry.getKey(), postMapping);

                    editor.getKeySelection().remapSelection(id, chEntry.getKey(), postMapping);
                }
            }
        }
        post = new HashMap<>();
        for (var id : ids) {
            post.put(id, Objects.requireNonNull(editor.getScene().saveObject(id)));
        }
        return true;
    }

    @Override
    public void undo(EditorState editor) {
        for (var entry : pre.entrySet()) {
            editor.getScene().setSavedObject(entry.getKey(), entry.getValue());
            editor.getScene().revertObject(entry.getKey());
        }

        for (var objEntry : preSelectionMapping.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                editor.getKeySelection().remapSelection(objEntry.getKey(), chEntry.getKey(), chEntry.getValue());
            }
        }
    }

    @Override
    public void redo(EditorState editor) {
        for (var entry : post.entrySet()) {
            editor.getScene().setSavedObject(entry.getKey(), entry.getValue());
            editor.getScene().revertObject(entry.getKey());
        }

        for (var objEntry : postSelectionMapping.entrySet()) {
            for (var chEntry : objEntry.getValue().entrySet()) {
                editor.getKeySelection().remapSelection(objEntry.getKey(), chEntry.getKey(), chEntry.getValue());
            }
        }
    }
}
