package com.igrium.replaylab.render2.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render2.RenderMetadata;
import com.igrium.replaylab.render2.SimpleTexture;
import com.igrium.replaylab.render2.VideoRenderer;
import com.igrium.replaylab.render2.encoder.Encoder;
import com.mojang.blaze3d.platform.GlConst;
import imgui.ImGui;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

/**
 * Captures the framebuffer into a texture during replay rendering.
 * <p>
 * Unlike {@link Encoder}, frame captures are stateless (aside from <code>setMetadata</code>)
 * <p>
 * Render configuration is persisted via {@link #writeJson} / {@link #readJson},
 * and optional UI controls can be exposed through {@link #drawProperties}.
 */
public abstract class FrameCapture {

    @Getter
    private final FrameCaptureType<?> type;

    @Setter
    private @Nullable RenderMetadata metadata;

    public @Nullable RenderMetadata tryGetMetadata() {
        return this.metadata;
    }

    public @NonNull RenderMetadata getMetadata() {
        if (this.metadata == null) throw new IllegalStateException("Render metadata has not been set!");
        return metadata;
    }

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

    public SimpleTexture generateTexture() {
        var meta = getMetadata();
        return new SimpleTexture(meta.width(), meta.height(), GlConst.GL_RGB);
    }

    /**
     * Capture a single frame.
     *
     * @param frameIdx The index of the frame to capture.
     * @param texture  Texture to render into (on the GPU)
     */
    public abstract void captureFrame(int frameIdx, SimpleTexture texture);

    public void drawProperties(EditorState editorState) {
        ImGui.text("This renderer has no configurable properties.");
    }
}
