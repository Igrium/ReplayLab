package com.igrium.replaylab.render.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.SimpleTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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
        Minecraft mc = Minecraft.getInstance();

        /// === RENDER ===
        MCVer.resizeMainWindow(mc, meta.width(), meta.height());
        MCVer.pushMatrix();
        mc.getMainRenderTarget().bindWrite(true);

        RenderSystem.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        PreRenderCallback.EVENT.invoker().preRender();

        if (mc.level != null && mc.player != null) {
            GameRendererAccessor gameRenderer = (GameRendererAccessor) mc.gameRenderer;
            Screen orgScreen = mc.screen;
            boolean orgPauseOnLostFocus = mc.options.pauseOnLostFocus;
            boolean orgRenderHand = gameRenderer.getRenderHand();

            try {
                mc.screen = null;
                mc.options.pauseOnLostFocus = false;
                // TODO: set render hand if omnidirectional

                mc.gameRenderer.render(mc.getDeltaTracker(), true);
            } finally {
                mc.screen = orgScreen;
                mc.options.pauseOnLostFocus = orgPauseOnLostFocus;
                gameRenderer.setRenderHand(orgRenderHand);
            }
        }

        PostRenderCallback.EVENT.invoker().postRender();

        mc.getMainRenderTarget().unbindWrite();
        MCVer.popMatrix();

        /// === SAVE FRAME ===
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
        GlStateManager._bindTexture(texture.getId());

        GlStateManager._glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
    }

}
