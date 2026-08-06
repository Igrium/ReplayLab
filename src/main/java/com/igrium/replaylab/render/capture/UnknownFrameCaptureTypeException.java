package com.igrium.replaylab.render.capture;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

public class UnknownFrameCaptureTypeException extends RuntimeException {
    @Getter
    private final ResourceLocation identifier;

    public UnknownFrameCaptureTypeException(ResourceLocation identifier) {
        super("Unknown frame capture type: " + identifier);
        this.identifier = identifier;
    }
}
