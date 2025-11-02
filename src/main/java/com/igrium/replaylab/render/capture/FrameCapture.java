package com.igrium.replaylab.render.capture;

import net.minecraft.client.texture.NativeImage;

/**
 * Responsible for capturing a Minecraft frame as an image.
 */
public interface FrameCapture {

    /**
     * Capture a frame.
     * @return A NativeImage with the frame contents.
     */
    NativeImage capture();

}
