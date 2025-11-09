package com.igrium.replaylab.render.writer;

import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Responsible for writing frames to disk.
 */
public interface FrameWriter {

    /**
     * Called as the video render begins.
     */
    default void begin() throws IOException {
    }

    /**
     * Save a frame to disk.
     *
     * @param image    Frame to save.
     * @param frameIdx The index of the current frame
     * @param autoFree If <code>true</code>, automatically close the image once serialization is complete.
     */
    void write(NativeImage image, int frameIdx, boolean autoFree);

    /**
     * Called as the video render ends.
     *
     * @return A future that completes once this writer has finished its finalization sequence.
     * All disk operations should have finished by this point.
     */
    default CompletableFuture<?> finish() {
        return CompletableFuture.completedFuture(null);
    }
}
