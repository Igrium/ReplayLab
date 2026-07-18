package com.igrium.replaylab.anim.constraint;

import com.google.gson.*;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.util.BiListMap;
import com.igrium.replaylab.util.NameUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple container to hold constraints in order to make ReplayObject less of a god class
 */
public final class ConstraintContainer {

    // Not using map.entry for gson serialization
    public record ConstraintEntry(String key, JsonObject value) {}

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ConstraintContainer");

    @Getter
    private final ReplayObject object;

    @Getter
    private final BiListMap<String, Constraint<?>> values = new BiListMap<>();

    public ConstraintContainer(ReplayObject object) {
        this.object = object;
    }


    /**
     * Add a constraint to this container.
     *
     * @param name       Name to assign.
     * @param constraint Constraint to add.
     * @param force      If <code>false</code>, make <code>name</code> unique instead of overwriting.
     * @return The previous constraint using that name, if any
     * @throws IllegalArgumentException If the supplied constraint belongs to the wrong object
     */
    public @Nullable Constraint<?> add(String name, Constraint<?> constraint, boolean force) throws IllegalArgumentException  {
        if (name.contains(":")) {
            throw new IllegalArgumentException("Constraint name cannot contain ':'");
        }
        if (constraint.getObject() != object) {
            throw new IllegalArgumentException("Constraint belongs to the wrong object!");
        }
        if (!force) {
            name = NameUtils.makeNameUnique(name, values::containsKey);
        }
        return values.put(name, constraint);
    }

    /**
     * Spawn a constraint and add it to this container.
     *
     * @param name  The name to assign.
     * @param type  The type to spawn.
     * @param force If <code>false</code>, make <code>name</code> unique instead of overwriting.
     * @return The new constraint
     * @throws ClassCastException If the supplied constraint is not applicable to this object
     */
    public @NotNull Constraint<?> create(String name, ConstraintType<?, ?> type, boolean force) throws ClassCastException {
        if (name.contains(":")) {
            throw new IllegalArgumentException("Constraint name cannot contain ':'");
        }
        if (!force) {
            name = NameUtils.makeNameUnique(name, values::containsKey);
        }
        var constraint = ConstraintType.create(type, object);
        values.put(name, constraint);
        return constraint;
    }

    public @Nullable Constraint<?> get(String name) {
        return values.get(name);
    }

    /**
     * Rename a constraint
     * @param oldName The old name of the constraint
     * @param newName The new name of the constraint
     * @return The new name after conflict resolution
     */
    public String rename(String oldName, String newName) {
        if (newName.contains(":")) {
            throw new IllegalArgumentException("Constraint name cannot contain ':'");
        }
        var constraint = get(oldName);
        if (constraint == null) {
            return "";
        }

        newName = NameUtils.makeNameUnique(newName, values::containsKey);
        values.inverse().put(constraint, newName);
        return newName;
    }

    public void evaluate(int time, ObjectAccessor objAccessor) {
        object.resetConstraintState();
        for (var entry : values.entryList()) {
            entry.getValue().evaluate(time, objAccessor);
        }
    }

    public List<ConstraintEntry> save(JsonSerializationContext ctx) {
        List<ConstraintEntry> list = new ArrayList<>(values.size());
        EditorState state = EditorState.getInstance();
        for (var entry : values.entryList()) {
            try {
                list.add(new ConstraintEntry(entry.getKey(), entry.getValue().save(ctx)));
            } catch (Exception e) {
                LOGGER.error("Error saving constraint {}", entry.getKey(), e);
                if (state != null) state.onException(e);
            }
        }
        return list;
    }

    public void parse(List<? extends ConstraintEntry> entries, JsonDeserializationContext ctx) {
        values.clear();
        EditorState state = EditorState.getInstance();
        for (var entry : entries) {
            // TODO: Find a cleaner way to show parse errors to the user
            try {
                values.put(entry.key(), ConstraintType.fromJson(object, entry.value(), ctx));
            } catch (Exception e) {
                LOGGER.error("Error parsing constraint {}", entry.key(), e);
                if (state != null) state.onException(e);
            }
        }
    }
}
