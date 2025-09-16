package com.igrium.replaylab.scene.obj;

import lombok.Getter;

public class InvalidObjectTypeException extends RuntimeException {

    @Getter
    private final String typeId;

    public InvalidObjectTypeException(String typeId) {
        super("Invalid object type: " + typeId);
        this.typeId = typeId;
    }
}
