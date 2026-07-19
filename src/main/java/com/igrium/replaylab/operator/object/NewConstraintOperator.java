package com.igrium.replaylab.operator.object;

import com.igrium.replaylab.anim.constraint.Constraint;
import com.igrium.replaylab.anim.constraint.ConstraintType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.ReplayObject;

import java.util.Objects;

public class NewConstraintOperator implements ReplayOperator {

    private final String objName;
    private final String name;
    private final ConstraintType<?, ?> type;

    private String actualName;

    public NewConstraintOperator(String objName, String name, ConstraintType<?, ?> type) {
        this.objName = objName;
        this.name = name;
        this.type = type;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        ReplayObject obj = editor.getScene().getObject(objName);
        if (obj == null) return false;


        Constraint<?> constraint = obj.getConstraints().create(name, type, false);
        actualName = constraint.getId();
        obj.save();
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        ReplayObject obj = getObject(editor, objName);
        obj.getConstraints().getValues().remove(actualName);
        obj.save();
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        ReplayObject obj = getObject(editor, objName);
        obj.getConstraints().create(actualName, type, true);
        obj.save();
    }

    private static ReplayObject getObject(EditorState editor, String id) {
        return Objects.requireNonNull(editor.getScene().getObject(id));
    }
}
