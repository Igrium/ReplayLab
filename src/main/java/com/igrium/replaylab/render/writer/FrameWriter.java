package com.igrium.replaylab.render.writer;

import net.minecraft.client.texture.NativeImage;

import java.util.concurrent.CompletableFuture;

public interface FrameWriter {

    /**
     * Called as the export process begins.
     */
    void start() throws Exception;

    /**
     * Called when it's time to write an individual frame.
     *
     * @param image    Native image with the frame's contents.
     * @param frameIdx The index of the current frame.
     * @apiNote The frame writer takes ownership of the supplied image.
     * All images will be freed by the time the writer exits.
     */
    void write(NativeImage image, int frameIdx) throws Exception;

    /**
     * Asynchronously finish the export process.
     *
     * @return A future that completes once all async write operations have finished.
     * If it is already in the process of finishing, return that future instead.
     */
    CompletableFuture<?> finish();
}
