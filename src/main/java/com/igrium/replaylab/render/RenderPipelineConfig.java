package com.igrium.replaylab.render;

import com.igrium.craftui.file.FileDialogs;

public interface RenderPipelineConfig {
    void drawConfig(VideoRenderSettings renderSettings);

    final class Dummy implements RenderPipelineConfig {

        @Override
        public void drawConfig(VideoRenderSettings renderSettings) {
            
        }
    }
}
