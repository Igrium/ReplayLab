package com.igrium.replaylab.render;

/**
 * Thrown if an exception occurs that interrupts the rendering process but does not warrant crashing the entire game.
 */
public class VideoRenderException extends Exception {
    private static final String DEFAULT_MSG = "An exception occurred rendering the video.";

    public VideoRenderException() {
        super(DEFAULT_MSG);
    }

    public VideoRenderException(Throwable cause) {
        super(DEFAULT_MSG, cause);
    }

    public VideoRenderException(String msg) {
        super(msg);
    }

    public VideoRenderException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
