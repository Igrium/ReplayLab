package com.igrium.replaylab.render;

import com.igrium.replaylab.util.RenderUtils;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.texture.AbstractTexture;

import java.io.Closeable;
import java.io.IOException;

/**
 * A simplified framebuffer which wraps a texture into its color attachment
 */
public class SimpleFramebuffer implements Closeable {

    /**
     * The ID of the underlying OpenGL framebuffer
     */
    @Getter
    private final int fbo;

    /**
     * The texture this framebuffer wraps
     */
    @Getter
    private final AbstractTexture texture;

    @Getter
    private boolean closed;

    public SimpleFramebuffer(AbstractTexture texture) {
        RenderSystem.assertOnRenderThread();
        this.texture = texture;
        fbo = GlStateManager.glGenFramebuffers();

        int prevFbo = GlStateManager.getBoundFramebuffer();
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GlConst.GL_COLOR_ATTACHMENT0,
                GlConst.GL_TEXTURE_2D, texture.getGlId(), 0);
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, prevFbo);
    }


    @Override
    public void close()  {
        closed = true;
        RenderUtils.onRenderThread(() -> GlStateManager._glDeleteFramebuffers(fbo));
    }
}
