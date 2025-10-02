package com.igrium.replaylab.operator;

import com.igrium.replaylab.scene.ReplayScene;

public class RenameObjectOperator implements ReplayOperator {
    private final String oldName;
    private final String newName;

    private String actualNewName;

    public RenameObjectOperator(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public boolean execute(ReplayScene scene) {
        if (oldName.equals(newName)) {
            return false;
        }
        actualNewName = scene.makeNameUnique(newName);
        return scene.renameObject(oldName, actualNewName);
    }

    @Override
    public void undo(ReplayScene scene) {
        scene.renameObject(actualNewName, oldName);
    }

    @Override
    public void redo(ReplayScene scene) {
        scene.renameObject(oldName, actualNewName);
    }
}
