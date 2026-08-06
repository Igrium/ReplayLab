package com.igrium.replaylab.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import lombok.Getter;

import java.io.Closeable;

/**
 * A simple off-screen texture we can render into and read back from.
 * <p>
 * Since 26.2 there is no public GL texture id to hang onto; this wraps the Blaze3D
 * {@link GpuTexture} and a view of it instead.
 */
public class SimpleTexture implements Closeable {

    /**
     * Usage flags every render texture needs: sampled by the UI, written to by the world render,
     * and read back by the encoder.
     */
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_RENDER_ATTACHMENT;

    @Getter
    private final int width;

    @Getter
    private final int height;

    @Getter
    private final GpuFormat format;

    @Getter
    private final GpuTexture texture;

    @Getter
    private final GpuTextureView textureView;

    @Getter
    private boolean closed;

    public SimpleTexture(int width, int height, GpuFormat format) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        RenderSystem.assertOnRenderThread();

        this.width = width;
        this.height = height;
        this.format = format;

        this.texture = RenderSystem.getDevice().createTexture("ReplayLab render target", USAGE, format, width, height, 1, 1);
        this.textureView = RenderSystem.getDevice().createTextureView(texture);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RenderUtils.onRenderThread(() -> {
            textureView.close();
            texture.close();
        });
    }
}
