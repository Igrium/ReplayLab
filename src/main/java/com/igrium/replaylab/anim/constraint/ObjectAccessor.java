package com.igrium.replaylab.anim.constraint;

import com.igrium.replaylab.scene.obj.ReplayObject;
import org.jetbrains.annotations.Nullable;

public interface ObjectAccessor {
    /**
     * Get another replay object in the scene from its ID. This will automatically call that object's
     * evaluateConstraints function if it hasn't already been called.
     *
     * @param id ID to search for
     * @return The object; <code>null</code> if no object by that ID was found.
     * @throws DependencyLoopException If a loop was detected in constraint dependencies
     */
    @Nullable ReplayObject getObject(String id) throws DependencyLoopException;
}
