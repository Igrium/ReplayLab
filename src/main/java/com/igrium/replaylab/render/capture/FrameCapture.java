package com.igrium.replaylab.render.capture;

import net.minecraft.client.texture.NativeImage;

/**
 * Responsible for capturing the current window.
 */
public interface FrameCapture {
    /**
     * Capture the current frame. Calls back into the VideoRenderer to update the scene.
     * @param frameIdx The current frame index.
     * @return A NativeImage containing the frame's current contents.
     */
    NativeImage capture(int frameIdx);
}
