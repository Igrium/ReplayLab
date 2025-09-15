package com.igrium.replaylab.scene.obj;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when you try to instantiate an animation object of an invalid type.
 */
public class InvalidObjectTypeException extends RuntimeException {

    @Getter
    private final @Nullable String type;

    public InvalidObjectTypeException(@Nullable String type) {
        super("Invalid animation object type: " + type);
        this.type = type;
    }
}
