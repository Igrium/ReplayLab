package com.igrium.replaylab.render;

import lombok.*;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

/**
 * The configuration passed to the video renderer when it's time to render.
 */
@Getter @Builder @AllArgsConstructor
public final class VideoRenderSettings {

    /**
     * The implementation responsible for capturing the Minecraft world into a texture.
     */
    @NonNull
    private final Identifier frameCapture;

    /**
     * The implementation responsible for saving frames to disk (image sequence, etc)
     */
    @NonNull
    private final Identifier frameWriter;

    /**
     * The folder to output to if saving an image sequence; the file to output to if saving a video.
     */
    @NonNull
    private final Path outputFile;

    /**
     * The width of the video in pixels
     */
    @Builder.Default
    private final int width = 1920;

    /**
     * The height of the video in pixels
     */
    @Builder.Default
    private final int height = 1080;

    /**
     * The frame rate of the video.
     */
    @Builder.Default
    private final float fps = 24;

    /**
     * Apply multi-sample motion blur to the scene
     */
    private final boolean motionBlur = false;

    /**
     * Shutter speed to use when applying motion blur. Only relevant if <code>motionBlur</code> is enabled.
     */
    private final float shutterSpeed = 0.5f;

    /**
     * The number of progressive refinement samples to use. <code>0</code> to disable progressive refinement.
     */
    private final int samples = 0;
}