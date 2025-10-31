package com.igrium.replaylab.render;

import net.minecraft.client.texture.NativeImage;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for serializing frames to disk.
 */
public interface FrameWriter extends AutoCloseable {

    /**
     * Submit an image to be serialized to disk. Images are serialized in the order this is called.
     *
     * @param frame     Image to save.
     * @param autoClose If true, automatically <code>close</code> the image when finished.
     * @return A future that completes once the image is finished saving.
     * @apiNote While this function may return asynchronously, it is <em>not</em> considered thread safe!
     *          Only call from the render thread.
     */
    CompletableFuture<?> write(NativeImage frame, boolean autoClose);

    /**
     * Stop writing frames and finish writing to disk.
     *
     * @throws Exception If an exception occurs finalizing the output.
     * @apiNote Only call once all futures returned by <code>write</code> are complete!
     * Behavior if there's still frames being saved is undefined.
     */
    @Override
    void close() throws Exception;
}
