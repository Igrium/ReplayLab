package com.igrium.replaylab.render.capture;

import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.versions.MCVer;
import com.replaymod.render.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.GL11;

public class OpenGLFrameCapture implements FrameCapture {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final VideoRenderer videoRenderer;
    private final VideoRenderSettings renderSettings;

    public OpenGLFrameCapture(VideoRenderer videoRenderer, VideoRenderSettings renderSettings) {
        this.videoRenderer = videoRenderer;
        this.renderSettings = renderSettings;
    }

    @Override
    public NativeImage capture(int frameIdx) {
        float tickDelta = videoRenderer.queueNextFrame();

        /// === RENDER ===
        MCVer.resizeMainWindow(mc, renderSettings.getWidth(), renderSettings.getHeight());
        MCVer.pushMatrix();
        mc.getFramebuffer().beginWrite(true);

        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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

        /// === Save Frame ===
        return ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
    }
}
