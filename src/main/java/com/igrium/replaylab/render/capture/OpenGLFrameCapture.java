package com.igrium.replaylab.render.capture;

import com.igrium.replaylab.render.ReplayWorldRenderer;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.versions.MCVer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.opengl.GL11;

public class OpenGLFrameCapture implements FrameCapture {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final ReplayWorldRenderer worldRenderer = new ReplayWorldRenderer();
    private final VideoRenderer videoRenderer;


    public OpenGLFrameCapture(VideoRenderer videoRenderer) {
        this.videoRenderer = videoRenderer;
    }

    @Override
    public NativeImage capture() {
        videoRenderer.queueNextFrame(0);

        MCVer.resizeMainWindow(mc, getSettings().getWidth(), getSettings().getHeight());
        MCVer.pushMatrix();
        Framebuffer framebuffer = mc.getFramebuffer();

        framebuffer.beginWrite(true);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        worldRenderer.renderWorld();
        framebuffer.endWrite();

        NativeImage img = new NativeImage(framebuffer.textureWidth, framebuffer.textureHeight, false);

        RenderSystem.bindTexture(framebuffer.getColorAttachment());
        img.loadFromTextureImage(0, true);
        img.mirrorVertically();

        return img;
    }

    private VideoRenderSettings getSettings() {
        return videoRenderer.getSettings();
    }

}
