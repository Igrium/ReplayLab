package com.igrium.replaylab.render2.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render2.RenderMetadata;
import com.igrium.replaylab.render2.SimpleTexture;
import com.igrium.replaylab.render2.VideoRenderer;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;

import static com.mojang.blaze3d.platform.GlConst.*;

public class BasicFrameCapture extends FrameCapture {
    public BasicFrameCapture(FrameCaptureType<?> type) {
        super(type);
    }

    @Override
    public void writeJson(JsonObject json, JsonSerializationContext context) {

    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {

    }

    @Override
    public SimpleTexture generateTexture() {
        return super.generateTexture();
    }

    @Override
    public void captureFrame(int frameIdx, SimpleTexture texture) {
        RenderSystem.assertOnRenderThread();

        RenderMetadata meta = getMetadata();
        MinecraftClient mc = MinecraftClient.getInstance();

        /// === RENDER ===
        MCVer.resizeMainWindow(mc, meta.width(), meta.height());
        MCVer.pushMatrix();
        mc.getFramebuffer().beginWrite(true);

        RenderSystem.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        PreRenderCallback.EVENT.invoker().preRender();

        if (mc.world != null && mc.player != null) {
            GameRendererAccessor gameRenderer = (GameRendererAccessor) mc.gameRenderer;
            Screen orgScreen = mc.currentScreen;
            boolean orgPauseOnLostFocus = mc.options.pauseOnLostFocus;
            boolean orgRenderHand = gameRenderer.getRenderHand();

            try {
                mc.currentScreen = null;
                mc.options.pauseOnLostFocus = false;
                // TODO: set render hand if omnidirectional

                mc.gameRenderer.render(mc.getRenderTickCounter(), true);
            } finally {
                mc.currentScreen = orgScreen;
                mc.options.pauseOnLostFocus = orgPauseOnLostFocus;
                gameRenderer.setRenderHand(orgRenderHand);
            }
        }

        PostRenderCallback.EVENT.invoker().postRender();

        mc.getFramebuffer().endWrite();
        MCVer.popMatrix();

        /// === SAVE FRAME ===
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, mc.getFramebuffer().fbo);
        GlStateManager._bindTexture(texture.getGlId());

        GlStateManager._glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                mc.getFramebuffer().textureWidth, mc.getFramebuffer().textureHeight);
    }

}
