package com.igrium.replaylab.render.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.SimpleTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

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

        // === RENDER ===
        // As of 26.3, the main render target is resized inside GameRenderer.render() to match
        // the window size captured during extract(), so we spoof the window size for the capture.
        RenderTarget target = mc.gameRenderer.mainRenderTarget();

        PreRenderCallback.EVENT.invoker().preRender();

        if (mc.level != null && mc.player != null) {
            Screen orgScreen = mc.gui.screen();
            boolean orgPauseOnLostFocus = mc.options.pauseOnLostFocus;
            boolean orgHudHidden = mc.gui.hud.isHidden();
            double orgChunkFadeInTime = mc.options.chunkSectionFadeInTime().get();
            int orgWidth = mc.getWindow().getWidth();
            int orgHeight = mc.getWindow().getHeight();

            try {
                mc.getWindow().setWidth(meta.width());
                mc.getWindow().setHeight(meta.height());
                // Chunk fade-in is timed off the wall clock, so it would be inconsistent across rendered frames.
                mc.options.chunkSectionFadeInTime().set(0.0);
                mc.gui.setScreen(null);
                mc.options.pauseOnLostFocus = false;
                // The HUD (and, with it, the held item) is toggled off for the duration of the capture.
                if (!orgHudHidden) {
                    mc.gui.hud.toggle();
                }

                // The world render is split into update / extract / render.
                // extract() picks up the spoofed window size; render() resizes the main target to match.
                mc.gameRenderer.update(mc.getDeltaTracker());
                mc.gameRenderer.extract(mc.getDeltaTracker(), true);
                mc.gameRenderer.render();
            } finally {
                mc.gui.setScreen(orgScreen);
                mc.options.pauseOnLostFocus = orgPauseOnLostFocus;
                mc.options.chunkSectionFadeInTime().set(orgChunkFadeInTime);
                mc.getWindow().setWidth(orgWidth);
                mc.getWindow().setHeight(orgHeight);
                if (mc.gui.hud.isHidden() != orgHudHidden) {
                    mc.gui.hud.toggle();
                }
            }
        }

        PostRenderCallback.EVENT.invoker().postRender();

        // The frame must be explicitly finished before its contents can be read.
        RenderSystem.getDynamicUniforms().reset();
        mc.levelRenderer.endFrame();
        RenderSystem.getDevice().createCommandEncoder().submit();

        // === SAVE FRAME ===
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int width = Math.min(target.width, texture.getWidth());
        int height = Math.min(target.height, texture.getHeight());
        encoder.copyTextureToTexture(target.getColorTexture(), texture.getTexture(), 0,
                0, 0, 0, 0, width, height);
    }

}
