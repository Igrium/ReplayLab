package com.igrium.replaylab.render.encoder;

/**
 * Thrown when the encoder encounters an error.
 */
public class EncoderException extends RuntimeException {
    public EncoderException(Throwable cause) {
        super("The encoder crashed:", cause);
    }
}
