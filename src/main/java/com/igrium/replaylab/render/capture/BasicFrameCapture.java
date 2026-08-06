package com.igrium.replaylab.render.capture;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.SimpleTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.versions.MCVer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Vector4f;

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
        RenderTarget target = mc.gameRenderer.mainRenderTarget();

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), new Vector4f(), target.getDepthTexture(), 0);

        PreRenderCallback.EVENT.invoker().preRender();

        if (mc.level != null && mc.player != null) {
            Screen orgScreen = mc.gui.screen();
            boolean orgPauseOnLostFocus = mc.options.pauseOnLostFocus;
            boolean orgHudHidden = mc.gui.hud.isHidden();

            try {
                mc.gui.setScreen(null);
                mc.options.pauseOnLostFocus = false;
                // The HUD (and, with it, the held item) is toggled off for the duration of the capture.
                if (!orgHudHidden) {
                    mc.gui.hud.toggle();
                }

                // 26.2 splits the world render into update / extract / render.
                mc.gameRenderer.update(mc.getDeltaTracker());
                mc.gameRenderer.extract(mc.getDeltaTracker(), true);
                mc.gameRenderer.render(mc.getDeltaTracker(), true);
            } finally {
                mc.gui.setScreen(orgScreen);
                mc.options.pauseOnLostFocus = orgPauseOnLostFocus;
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

        /// === SAVE FRAME ===
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int width = Math.min(target.width, texture.getWidth());
        int height = Math.min(target.height, texture.getHeight());
        encoder.copyTextureToTexture(target.getColorTexture(), texture.getTexture(), 0,
                0, 0, 0, 0, width, height);
    }

}
