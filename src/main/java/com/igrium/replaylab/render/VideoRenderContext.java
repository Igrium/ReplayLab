package com.igrium.replaylab.render;

import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;

@Getter
public class VideoRenderContext {
    private final ReplayScene scene;

    private final int width;
    private final int height;

    private final float fps;

    public VideoRenderContext(ReplayScene scene, int width, int height, float fps) {
        this.scene = scene;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }
}
