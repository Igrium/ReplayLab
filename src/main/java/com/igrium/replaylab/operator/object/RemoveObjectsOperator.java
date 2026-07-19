package com.igrium.replaylab.operator.object;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.object.ReplayObject;

import java.util.*;

public class RemoveObjectsOperator implements ReplayOperator {

    private final Set<String> objIds;
    private final Map<String, ReplayObject> objs = new HashMap<>();

    public RemoveObjectsOperator(Collection<? extends String> objIds) {
        this.objIds = Set.copyOf(objIds);
    }

    public RemoveObjectsOperator(String... objIds) {
        this.objIds = Set.of(objIds);
    }

    @Override
    public boolean execute(EditorState editor) {
        boolean success = false;
        for (String objId : objIds) {
            if (objId.equals(ReplayScene.SCENE_PROPS)) continue; // Don't delete scene props
            var obj = editor.getScene().removeObject(objId);
            if (obj != null) {
                objs.put(objId, obj);
                success = true;
            }
        }
        if (success) {
            editor.getSelectedObjects().removeAll(objs.keySet());
        }
        return success;
    }

    @Override
    public void undo(EditorState editor) {
        objs.forEach(editor.getScene()::addObject);
    }

    @Override
    public void redo(EditorState editor) {
        objs.keySet().forEach(editor.getScene()::removeObject);
        editor.getSelectedObjects().removeAll(objs.keySet());
    }
}
