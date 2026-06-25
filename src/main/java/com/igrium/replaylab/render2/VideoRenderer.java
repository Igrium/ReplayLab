package com.igrium.replaylab.render2;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render2.capture.FrameCapture;
import com.igrium.replaylab.render2.capture.FrameCaptureType;
import com.igrium.replaylab.render2.encoder.EncoderProcess;
import com.igrium.replaylab.render2.encoder.EncoderType;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    private volatile boolean abort;

    @Getter
    private @Nullable SimpleTexture renderTexture;

    public VideoRenderer(RenderMetadata renderMetadata, ReplayHandler replay, ReplayScene scene) {
        this.renderMetadata = renderMetadata;
        this.replay = replay;
        this.scene = scene;
        guiWindow = new VirtualWindow(mc);
    }

    public void abort() {
        this.abort = true;
    }

    /**
     * Render the video
     *
     * @return <code>true</code> if the rendering was successful; <code>false</code> if the user aborted rendering or
     * the window was closed
     */
    public boolean render() throws Exception {
        RenderSystem.assertOnRenderThread();
        renderState = RenderState.STARTING;
        renderingVideo = true;

        boolean wasAsyncMode = replay.getReplaySender().isAsyncMode();
        boolean debugWasShown = mc.getDebugHud().shouldShowDebugHud();
        boolean mouseWasGrabbed = mc.mouse.isCursorLocked();
        EnumMap<SoundCategory, Float> originalSoundLevels = new EnumMap<>(SoundCategory.class);
        ForceChunkLoadingHook forceChunkLoadingHook = null;

        totalFrames = getRenderMetadata().totalFrames();
        CompletableFuture<?> scenePlayerFuture = null;
        RenderScenePlayer scenePlayer = null;
        try {
            /// === SETUP ===
            replay.getReplaySender().setAsyncMode(false);

            ScenePropsObject sceneProps = scene.getSceneProps();
            VideoRenderSettings settings = sceneProps.getRenderSettings();

            // TODO: read from config
            FrameCapture capture = FrameCaptureType.BASIC.create();
            capture.setMetadata(renderMetadata);

            EncoderProcess encoder = EncoderType.PNG.create().spawnEncoder();

            scenePlayer = new RenderScenePlayer(replay);
            scenePlayerFuture = scenePlayer.start(scene);

            if (debugWasShown) {
                mc.getDebugHud().toggleDebugHud();
            }

            mc.mouse.unlockCursor();

            for (var category : SoundCategory.values()) {
                if (category != SoundCategory.MASTER) {
                    originalSoundLevels.put(category, mc.options.getSoundVolume(category));
                    mc.options.getSoundVolumeOption(category).setValue(0d);
                }
            }

            forceChunkLoadingHook = new ForceChunkLoadingHook(mc.worldRenderer);

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
            encoder.start(renderMetadata);
            renderTexture = capture.generateTexture();

            renderState = RenderState.RENDERING;
            while (frameIdx < totalFrames && !abort) {
                if (GLFW.glfwWindowShouldClose(mc.getWindow().getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
                    encoder.finish().get(10, TimeUnit.SECONDS);
                }
                int curIdx = frameIdx;
                queueFrame(frameIdx, 1);
                capture.captureFrame(curIdx, renderTexture);

                drawGui();
                NativeImage nImage = new NativeImage(renderMetadata.width(), renderMetadata.height(), true);
                RenderSystem.bindTexture(renderTexture.getGlId());
                nImage.loadFromTextureImage(0, true);
                nImage.mirrorVertically();

                Throwable e = encoder.getFailureReason();
                if (e != null) {
                    throw (Exception) e;
                }
                encoder.accept(ManagedNativeImage.of(nImage), curIdx);

            }

            /// === FINISH ===
            renderState = RenderState.FINISHING;
            CompletableFuture<?> finishFuture = encoder.finish().orTimeout(30000, TimeUnit.MILLISECONDS);
            while (!finishFuture.isDone()) {
                drawGui();
                //noinspection BusyWait
                Thread.sleep(10);

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }

            if (finishFuture.isCompletedExceptionally()) {
                Throwable e = finishFuture.exceptionNow();
                throw e instanceof Exception ex ? ex : ExceptionUtils.asRuntimeException(e);
            }

            if (((MinecraftAccessor) mc).getCrashReporter() != null) {
                throw new CrashException(((MinecraftAccessor) mc).getCrashReporter().get());
            }

            // TODO: spherical metadata


            return !abort;
        } finally {
            if (renderTexture != null) {
                try {
                    renderTexture.close();
                } catch (Exception e) {
                    LOGGER.error("Error while closing texture", e);
                }
                renderTexture = null;
            }

            renderingVideo = false;
            renderState = RenderState.DONE;


            if (!scenePlayerFuture.isDone()) {
                scenePlayerFuture.cancel(false);
            }

            // Tear down of the timeline player might only happen the next tick after it was canceled
            scenePlayer.onTick();

            if (debugWasShown) {
                mc.getDebugHud().toggleDebugHud();
            }

            if (mouseWasGrabbed) {
                mc.mouse.lockCursor();
            }

            for (var entry : originalSoundLevels.entrySet()) {
                mc.options.getSoundVolumeOption(entry.getKey()).setValue(Double.valueOf(entry.getValue()));
            }

            if (forceChunkLoadingHook != null) {
                forceChunkLoadingHook.uninstall();
            }

            mc.setScreen(null);

            mc.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvent.of(Identifier.of("replaymod:render_success")), 1));

            // Finally, resize the Minecraft framebuffer to the actual width/height of the window
            MCVer.resizeMainWindow(mc, guiWindow.getFramebufferWidth(), guiWindow.getFramebufferHeight());

            if (!wasAsyncMode) {
                replay.getReplaySender().setAsyncMode(false);
            }
        }
    }


    public float queueFrame(int sampleIdx, int totalSamples) {
        guiWindow.bind();

        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer(); // Updating the timer will cause the
        // timeline player to update the game state
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
