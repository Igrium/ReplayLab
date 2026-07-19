package com.igrium.replaylab.operator.constraint;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.igrium.replaylab.anim.constraint.Constraint;
import com.igrium.replaylab.anim.constraint.ConstraintType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.json.GsonSerializationContext;
import com.igrium.replaylab.operator.ReplayOperator;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.util.BiListMap;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RemoveConstraintOperator implements ReplayOperator {

    private static final GsonSerializationContext ctx = new GsonSerializationContext(new Gson());

    private final String objName;
    private final String constraintName;

    private @Nullable JsonObject serialized;
    private int index = -1;

    public RemoveConstraintOperator(String objName, String constraintName) {
        this.objName = objName;
        this.constraintName = constraintName;
    }

    @Override
    public boolean execute(EditorState editor) throws Exception {
        ReplayObject obj = editor.getScene().getObject(objName);
        if (obj == null) return false;

        BiListMap<String, Constraint<?>> values = obj.getConstraints().getValues();
        index = indexOf(values, constraintName);
        if (index < 0) return false;

        Constraint<?> constraint = values.remove(constraintName);
        if (constraint == null) return false;

        serialized = constraint.save(ctx);
        return true;
    }

    @Override
    public void undo(EditorState editor) throws Exception {
        ReplayObject obj = editor.getScene().getObject(objName);
        Objects.requireNonNull(obj);

        Constraint<?> constraint = ConstraintType.fromJson(obj, Objects.requireNonNull(serialized), ctx);

        BiListMap<String, Constraint<?>> values = obj.getConstraints().getValues();
        List<Map.Entry<String, Constraint<?>>> entryList = values.entryList();
        int insertAt = Math.min(index, entryList.size());
        entryList.add(insertAt, new SimpleImmutableEntry<>(constraintName, constraint));
    }

    @Override
    public void redo(EditorState editor) throws Exception {
        ReplayObject obj = editor.getScene().getObject(objName);
        Objects.requireNonNull(obj);

        obj.getConstraints().getValues().remove(constraintName);
    }

    private static int indexOf(BiListMap<String, Constraint<?>> values, String key) {
        List<Map.Entry<String, Constraint<?>>> entryList = values.entryList();
        for (int i = 0; i < entryList.size(); i++) {
            if (Objects.equals(entryList.get(i).getKey(), key)) {
                return i;
            }
        }
        return -1;
    }
}