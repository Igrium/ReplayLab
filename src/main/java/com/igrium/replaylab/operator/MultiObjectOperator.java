package com.igrium.replaylab.operator;

import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import org.slf4j.Logger;

import java.util.*;

/**
 * An operator that affects multiple objects and uses serialization for undo/redo
 */
public abstract class MultiObjectOperator implements ReplayOperator {

    private static final Logger LOGGER = ReplayLab.getLogger("MultiObjectOperator");

    private Map<String, SerializedReplayObject> pre;
    private Map<String, SerializedReplayObject> post;

    /**
     * Get all the objects that this operator will affect.
     * @param editor The current editor state
     * @return A collection of all affected objects.
     */
    protected abstract Collection<? extends String> getTargetObjects(EditorState editor);

    @Override
    public final boolean execute(EditorState editor) throws Exception {
        var objNames = getTargetObjects(editor);
        Map<String, ReplayObject> objs = new HashMap<>(objNames.size());
        for (var objName : objNames) {
            ReplayObject obj = editor.getScene().getObject(objName);
            if (obj != null) {
                objs.put(objName, obj);
            } else {
                LOGGER.warn("No object found with id: {}", objName);
            }
        }

        if (objs.isEmpty()) return false;

        this.pre = new HashMap<>();
        for (var objName : objs.keySet()) {
            var pre = editor.getScene().getSavedObject(objName);
            if (pre == null) {
                pre = editor.getScene().saveObject(objName);
            }
            this.pre.put(objName, pre);
        }

        if (!execute(editor, Collections.unmodifiableMap(objs))) return false;

        this.post = new HashMap<>();
        for (var objName : objs.keySet()) {
            var post = editor.getScene().saveObject(objName);
            this.post.put(objName, post);
        }
        return true;
    }

    /**
     * Execute this operator
     * @param editor The current editor state
     * @param objects The objects to operate on.
     * @return If the operation was successful.
     */
    protected abstract boolean execute(EditorState editor, Map<String, ReplayObject> objects) throws Exception;

    @Override
    public void undo(EditorState editor) throws Exception {
        for (var entry : pre.entrySet()) {
            editor.getScene().setSavedObject(entry.getKey(), entry.getValue());
            editor.getScene().revertObject(entry.getKey());
        }
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        for (var entry : post.entrySet()) {
            editor.getScene().setSavedObject(entry.getKey(), entry.getValue());
            editor.getScene().revertObject(entry.getKey());
        }
    }
}
