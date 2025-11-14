package com.igrium.replaylab.render;

import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.capture.FrameCaptureType;
import com.igrium.replaylab.render.writer.FrameWriter;
import com.igrium.replaylab.render.writer.FrameWriterType;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashException;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.concurrent.*;

public class VideoRenderer {

    private static final Logger LOGGER = ReplayLab.getLogger("VideoRenderer");

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final VideoRenderSettings settings;
    private final ReplayHandler replay;
    private final ReplayScene scene;

    private final VirtualWindow guiWindow = new VirtualWindow(mc);

    @Getter
    private int frameIdx = 0;

    /**
     * The total number of frames to render. Not initialized until render process starts!
     */
    @Getter
    private int totalFrames;

    private volatile boolean abort;

    public VideoRenderer(VideoRenderSettings settings, ReplayHandler replay, ReplayScene scene) {
        this.settings = settings;
        this.replay = replay;
        this.scene = scene;
    }

    public void abort() {
        this.abort = true;
    }

    /**
     * Render the video
     * @return <code>true</code> if the rendering was successful; <code>false</code> if the user aborted rendering or the window was closed
     * @throws Throwable If a fatal exception occurs while rendering
     */
    public boolean render() throws Throwable {

        RenderSystem.assertOnRenderThread();

        /// === SETUP ===
        boolean wasAsyncMode = replay.getReplaySender().isAsyncMode();
        replay.getReplaySender().setAsyncMode(false);

        FrameCapture capture = spawnFrameCapture(settings.getFrameCapture());
        FrameWriter writer = spawnFrameWriter(settings.getFrameWriter());

        RenderScenePlayer scenePlayer = new RenderScenePlayer(replay);
        scenePlayer.start(scene);
        CompletableFuture<?> scenePlayerFuture = scenePlayer.getFuture();

        boolean debugWasShown = mc.getDebugHud().shouldShowDebugHud();
        if (debugWasShown) {
            mc.getDebugHud().toggleDebugHud();
        }

        boolean mouseWasGrabbed = mc.mouse.isCursorLocked();
        mc.mouse.unlockCursor();

        EnumMap<SoundCategory, Float> originalSoundLevels = new EnumMap<>(SoundCategory.class);
        for (var category : SoundCategory.values()) {
            if (category != SoundCategory.MASTER) {
                originalSoundLevels.put(category, mc.options.getSoundVolume(category));
                mc.options.getSoundVolumeOption(category).setValue(0d);
            }
        }

        float fps = settings.getFps();
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
        writer.start();

        while (frameIdx < totalFrames && !abort) {
            if (GLFW.glfwWindowShouldClose(mc.getWindow().getHandle()) || ((MinecraftAccessor) mc).getCrashReporter() != null) {
                writer.finish();
            }
            NativeImage frame = capture.capture(frameIdx); // Internally calls queueNextFrame, triggering UI updates.
            writer.write(frame, frameIdx);
            LOGGER.info("Writing frame {} / {}", frameIdx, totalFrames);
        }

        // TODO: busy wait so we can update UI
        try {
            writer.finish().get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.error("Frame writer timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | CancellationException e) {
            LOGGER.error("Frame writer crashed: ", e.getCause());
        }

        /// === FINISH ===
        if (((MinecraftAccessor) mc).getCrashReporter() != null) {
            throw new CrashException(((MinecraftAccessor) mc).getCrashReporter().get());
        }

        // TODO: spherical metadata

        if (scenePlayerFuture != null && !scenePlayerFuture.isDone()) {
            scenePlayerFuture.cancel(false);
        }

        // Tear down of the timeline player might only happen the next tick after it was cancelled
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

        mc.setScreen(null);
        forceChunkLoadingHook.uninstall();

        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.of("replaymod:render_success")), 1));

        // Finally, resize the Minecraft framebuffer to the actual width/height of the window
        MCVer.resizeMainWindow(mc, guiWindow.getFramebufferWidth(), guiWindow.getFramebufferHeight());

        if (!wasAsyncMode) {
            replay.getReplaySender().setAsyncMode(false);
        }

        return !abort;
    }

    /**
     * Queue the next frame to be rendered.
     * @return The tick delta used for this frame
     */
    public float queueNextFrame() {
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
        scene.spectateCamera(getVideoTime());
        frameIdx++;
        return timer.tickDelta;
    }

    private void executeTaskQueue() {
        while (true) {
            while (mc.getOverlay() != null) {
                // TODO: GUI update
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


    public int getVideoTime() {
        return (int) (frameIdx * 1000 / settings.getFps());
    }

    private <T extends FrameCapture, C> T spawnFrameCapture(FrameCaptureType<T, C> type) {
        C config = type.getConfigClass().cast(settings.getFrameCaptureConfig());
        return type.create(this, settings, config);
    }

    private <T extends FrameWriter, C> T spawnFrameWriter(FrameWriterType<T, C> type) {
        C config = type.getConfigClass().cast(settings.getFrameWriterConfig());
        return type.create(this, settings, config);
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
