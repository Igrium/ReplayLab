package com.igrium.replaylab.operator;

import com.igrium.replaylab.editor.EditorState;

public class RenameObjectOperator implements ReplayOperator {
    private final String oldName;
    private final String newName;

    private String actualNewName;

    public RenameObjectOperator(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public boolean execute(EditorState scene) {
        if (oldName.equals(newName)) {
            return false;
        }
        actualNewName = scene.getScene().makeNameUnique(newName);
        return scene.getScene().renameObject(oldName, actualNewName);
    }

    @Override
    public void undo(EditorState scene) {
        scene.getScene().renameObject(actualNewName, oldName);
    }

    @Override
    public void redo(EditorState scene) {
        scene.getScene().renameObject(oldName, actualNewName);
    }
}
