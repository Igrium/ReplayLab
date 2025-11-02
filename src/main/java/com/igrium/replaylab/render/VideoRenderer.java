package com.igrium.replaylab.render;

import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.capture.FrameCaptures;
import com.igrium.replaylab.render.writer.FrameWriter;
import com.igrium.replaylab.render.writer.FrameWriters;
import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;

public class VideoRenderer {
    @Getter
    private final ReplayScene scene;

    @Getter
    private final VideoRenderSettings settings;

    private FrameCapture frameCapture;
    private FrameWriter frameWriter;

    public VideoRenderer(ReplayScene scene, VideoRenderSettings settings) {
        this.scene = scene;
        this.settings = settings;
    }

    public synchronized void render() {
        frameCapture = FrameCaptures.create(settings.getFrameCapture(), this);
        frameWriter = FrameWriters.create(settings.getFrameWriter(), this);


    }
}
