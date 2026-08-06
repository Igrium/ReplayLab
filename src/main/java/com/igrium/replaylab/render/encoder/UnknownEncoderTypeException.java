package com.igrium.replaylab.render.encoder;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

public class UnknownEncoderTypeException extends RuntimeException {
    @Getter
    private final ResourceLocation identifier;

    public UnknownEncoderTypeException(ResourceLocation identifier) {
        super("Unknown encoder type: " + identifier);
        this.identifier = identifier;
    }
}
