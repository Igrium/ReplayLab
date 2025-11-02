package com.igrium.replaylab.render.writer;

import net.minecraft.client.texture.NativeImage;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for writing frames to disk.
 */
public interface FrameWriter {

    /**
     * Called as the video render begins.
     *
     * @return A future that completes once this writer has finished its startup sequence.
     */
    default CompletableFuture<?> begin() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Save a frame to disk.
     *
     * @param image    Frame to save.
     * @param autoFree If <code>true</code>, automatically close the image once serialization is complete.
     * @return A future that completes once the image is saved.
     */
    CompletableFuture<?> write(NativeImage image, boolean autoFree);

    /**
     * Called as the video render ends.
     *
     * @return A future that completes once this writer has finished its finalization sequence.
     * All futures returned by <code>write</code> should have resolved by this point.
     */
    default CompletableFuture<?> finish() {
        return CompletableFuture.completedFuture(null);
    }
}
