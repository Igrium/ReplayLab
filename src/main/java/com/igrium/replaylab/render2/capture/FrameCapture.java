package com.igrium.replaylab.render2.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render2.RenderMetadata;
import com.igrium.replaylab.render2.SimpleTexture;
import com.igrium.replaylab.render2.VideoRenderer;
import com.mojang.blaze3d.platform.GlConst;
import lombok.Getter;

public abstract class FrameCapture {

    public interface QueueFrameCallback {
        float queueframe(int sampleIdx, int totalSamples);
    }

    @Getter
    private final FrameCaptureType<?> type;

    public FrameCapture(FrameCaptureType<?> type) {
        this.type = type;
    }

    /**
     * Write this capture's properties to Json
     *
     * @param json    Json object to write to
     * @param context Json serialization context
     */
    public abstract void writeJson(JsonObject json, JsonSerializationContext context);

    public abstract void readJson(JsonObject json, JsonDeserializationContext context);

    public SimpleTexture generateTexture(int width, int height) {
        return new SimpleTexture(width, height, GlConst.GL_RGBA);
    }

    /**
     * Capture a single frame.
     *
     * @param frameIdx The index of the frame to capture.
     * @param texture  Texture to render into (on the GPU)
     * @param renderer Current renderer
     * @param callback A callback to queue the next frame for rendering
     */
    public abstract void captureFrame(int frameIdx, SimpleTexture texture, VideoRenderer renderer, QueueFrameCallback callback);
}
