package com.igrium.replaylab.render2.capture;

import lombok.Getter;
import net.minecraft.util.Identifier;

public class UnknownFrameCaptureTypeException extends RuntimeException {
    @Getter
    private final Identifier identifier;

    public UnknownFrameCaptureTypeException(Identifier identifier) {
        super("Unknown frame capture type: " + identifier);
        this.identifier = identifier;
    }
}
