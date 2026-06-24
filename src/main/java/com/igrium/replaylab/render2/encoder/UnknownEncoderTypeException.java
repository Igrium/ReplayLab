package com.igrium.replaylab.render2.encoder;

import lombok.Getter;
import net.minecraft.util.Identifier;

public class UnknownEncoderTypeException extends RuntimeException {
    @Getter
    private final Identifier identifier;

    public UnknownEncoderTypeException(Identifier identifier) {
        super("Unknown encoder type: " + identifier);
        this.identifier = identifier;
    }
}
