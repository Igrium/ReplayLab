package com.igrium.replaylab.anim.constraint;

import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ConstraintEvaluator implements ObjectAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ConstraintEvaluator");

    private final Map<? super String, ? extends ReplayObject> objects;

    /**
     * The timestamp to evaluate at
     */
    @Getter
    private final int time;

    /**
     * All the values which have been completed
     */
    private final Set<String> completed = new HashSet<>();

    /**
     * Objects which are currently being evaluated (prevents recursion)
     */
    private final Set<String> inProgress = new HashSet<>();

    public ConstraintEvaluator(Map<? super String, ? extends ReplayObject> objects, int time) {
        this.objects = objects;
        this.time = time;
    }

    @Override
    public @Nullable ReplayObject getObject(String id) throws DependencyLoopException {
        if (inProgress.contains(id)) {
            throw new DependencyLoopException(id);
        }

        return evaluate(id);
    }

    public @Nullable ReplayObject evaluate(String id) {
        EditorState editorState = EditorState.getInstance();

        ReplayObject obj = objects.get(id);
        if (obj == null || completed.contains(id)) return obj;

        try {
            inProgress.add(id);

            try {
                obj.getConstraints().evaluate(time, this);
            } catch (Exception e) {
                LOGGER.error("Error evaluating constraint {}", id, e);
                if (editorState != null) editorState.onException(e);
            }

        } finally {
            inProgress.remove(id);
            completed.add(id);
        }

        return obj;
    }
}
