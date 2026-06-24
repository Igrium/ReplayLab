package com.igrium.replaylab.render2;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.render2.capture.FrameCapture;
import com.igrium.replaylab.render2.encoder.Encoder;
import com.igrium.replaylab.render2.encoder.EncoderType;
import com.igrium.replaylab.scene.ReplayScene;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.core.mixin.TimerAccessor;
import com.replaymod.core.utils.Utils;
import com.replaymod.core.versions.MCVer;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.render.gui.progress.VirtualWindow;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.replay.ReplayHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VideoRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/VideoRenderer");

    @Getter
    private static boolean renderingVideo;

    public enum RenderState {
        READY,
        STARTING,
        RENDERING,
        FINISHING,
        DONE
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    private final RenderMetadata renderMetadata;

    @Getter
    private final ReplayHandler replay;

    @Getter
    private final ReplayScene scene;

    private final VirtualWindow guiWindow;

    @Getter
    private int frameIdx = 0;

    private int totalFrames;

    public int getTotalFrames() {
        return renderMetadata.totalFrames();
    }

    @Getter
    private RenderState renderState = RenderState.READY;

    @Setter
    private @Nullable Consumer<Throwable> exceptionCallback;

    private volatile boolean abort;

    @Getter
    private @Nullable SimpleTexture renderTexture;

    public VideoRenderer(RenderMetadata renderMetadata, ReplayHandler replay, ReplayScene scene) {
        this.renderMetadata = renderMetadata;
        this.replay = replay;
        this.scene = scene;
        guiWindow = new VirtualWindow(mc);
    }

    /**
     * Render the video
     *
     * @return <code>true</code> if the rendering was successful; <code>false</code> if the user aborted rendering or the window was closed
     * @throws Throwable If a fatal exception occurs while rendering
     */
    public boolean render() throws Exception {
        RenderSystem.assertOnRenderThread();
        renderState = RenderState.STARTING;
        renderingVideo = true;
        try {
            /// === SETUP ===
            boolean wasAsyncMode = replay.getReplaySender().isAsyncMode();
            replay.getReplaySender().setAsyncMode(false);

            FrameCapture capture = new FrameCapture(); // TODO: make frame capture
            Encoder encoder = EncoderType.PNG.create();

            RenderScenePlayer scenePlayer = new RenderScenePlayer(replay);
            scenePlayer.start(scene);
            CompletableFuture<?> scenePlayerFuture = scenePlayer.getFuture();

            boolean debugWasShown = mc.getDebugHud().shouldShowDebugHud();
            boolean mouseWasGrabbed = mc.mouse.isCursorLocked();
            mc.mouse.unlockCursor();

            EnumMap<SoundCategory, Float> originalSoundLevels = new EnumMap<>(SoundCategory.class);
            for (var category : SoundCategory.values()) {
                if (category != SoundCategory.MASTER) {
                    originalSoundLevels.put(category, mc.options.getSoundVolume(category));
                    mc.options.getSoundVolumeOption(category).setValue(0d);
                }
            }

            float fps = scene.getFps();
            int duration = scene.getLength();

            totalFrames = (int) (duration * fps / 1000);

            ForceChunkLoadingHook forceChunkLoadingHook = new ForceChunkLoadingHook(mc.worldRenderer);

            /// === TIMELINE SETUP ===
            // I have no idea what mixin bullshit replay mod is doing, but I'll just copy it
            ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer();

            // Play up to one second before starting render to set entity positions
            int videoStart = scene.getStartTime();

            if (videoStart > 1000) {
                int replayTime = videoStart - 1000;
                timer.tickDelta = 0;

                ((TimerAccessor) timer).setTickLength(Utils.DEFAULT_MS_PER_TICK);
                while (replayTime < videoStart) {
                    replayTime += 50;
                    replay.getReplaySender().sendPacketsTill(replayTime);
                    mc.tick();
                }
            }

            /// === RENDERING PIPELINE ===
            int width = scene.getSceneProps().getResolutionX();
            int height = scene.getSceneProps().getResolutionY();
            renderTexture = capture.generateTexture(width, height);
            encoder.start(renderMetadata);
            renderState = RenderState.RENDERING;

            while (frameIdx < totalFrames && !abort) {
                if (GLFW.glfwWindowShouldClose(mc.getWindow().getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
                    encoder.finish().wait(20000);
                }
                capture.captureFrame(frameIdx, renderTexture, this, this::queueFrame);
                NativeImage nImage = new NativeImage(width, height, false);
                renderTexture.bindTexture();
                nImage.loadFromTextureImage(0, true);

                drawGui();
                encoder.accept(ManagedNativeImage.of(nImage), frameIdx);
            }

            /// === FINISH ===


        } finally {
            renderingVideo = false;
            renderState = RenderState.DONE;
            if (renderTexture != null) renderTexture.close();
            renderTexture = null;
        }
    }

    public float queueFrame(int sampleIdx, int totalSamples) {
        guiWindow.bind();


        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer(); // Updating the timer will cause the timeline player to update the game state
        try {
            // TODO: GUI update
            int elapsedTicks = timer.beginRenderTick(Util.getMeasuringTimeMs(), true);
            executeTaskQueue();

            while (elapsedTicks-- > 0) {
                mc.tick();
            }
        } finally {
            guiWindow.unbind();
        }

        // TODO: camera path exporter
        scene.spectateCamera();
        frameIdx++;

//        drawGui();
        return timer.tickDelta;
    }

    private void executeTaskQueue() {
        while (true) {
            while (mc.getOverlay() != null) {
//                drawGui();
                ((MCVer.MinecraftMethodAccessor) mc).replayModExecuteTaskQueue();
            }

            // I'll be real, I don't really know what this is doing but I'll copy it
            CompletableFuture<Void> resourceReloadFuture = ((MinecraftAccessor) mc).getResourceReloadFuture();
            if (resourceReloadFuture != null) {
                ((MinecraftAccessor) mc).setResourceReloadFuture(null);
                mc.reloadResources().thenRun(() -> resourceReloadFuture.complete(null));
                continue;
            }
            break;
        }

        ((MCVer.MinecraftMethodAccessor) mc).replayModExecuteTaskQueue();
    }

    public boolean drawGui() {
        Window window = mc.getWindow();
        if (GLFW.glfwWindowShouldClose(window.getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
            return false;
        }

//            MCVer.pushMatrix();

        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        guiWindow.beginWrite();

//        RenderSystem.clear(256);

        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
        drawContext.draw();

        guiWindow.endWrite();

        MCVer.pushMatrix();
        AppManager.render(mc);
//        guiWindow.flip();
        mc.getWindow().swapBuffers(null);
        MCVer.popMatrix();

        if (mc.mouse.isCursorLocked()) {
            mc.mouse.unlockCursor();
        }

        return !abort;
    }

    public int getVideoTime() {
        return (int) (frameIdx * 1000 / scene.getFps());
    }

    private class RenderScenePlayer extends AbstractScenePlayer {

        public RenderScenePlayer(@NonNull ReplayHandler replayHandler) {
            super(replayHandler);
        }

        @Override
        public int getTimePassed() {
            return getVideoTime();
        }
    }
}
