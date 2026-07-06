package com.igrium.replaylab.render;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import lombok.Getter;
import net.minecraft.client.texture.AbstractTexture;

public class SimpleTexture extends AbstractTexture {
    @Getter
    private final int width;

    @Getter
    private final int height;

    @Getter
    private final int internalFormat;

    public SimpleTexture(int width, int height, int internalFormat) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        this.width = width;
        this.height = height;
        this.internalFormat = internalFormat;
        RenderUtils.onRenderThread(this::prepareImage);
    }

    private void prepareImage() {
        int glId = getGlId();
        GlStateManager._bindTexture(glId);
        GlStateManager._texImage2D(GlConst.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                GlConst.GL_RGBA, GlConst.GL_UNSIGNED_BYTE, null);

        GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MIN_FILTER, GlConst.GL_LINEAR);
        GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MAG_FILTER, GlConst.GL_LINEAR);
    }

    @Override
    public void close() {
        super.close();
        clearGlId();
    }
}
