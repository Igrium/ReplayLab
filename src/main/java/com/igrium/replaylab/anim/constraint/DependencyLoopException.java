package com.igrium.replaylab.anim.constraint;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a loop is detected in constraint dependencies
 */
public class DependencyLoopException extends Exception {
    /**
     * The ID of the object dependency
     */
    @Getter
    private final String objId;

    public DependencyLoopException(String objId) {
        super("A loop was detected in constraint dependencies: " + objId);
        this.objId = objId;
    }
}
