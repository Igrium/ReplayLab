package com.igrium.replaylab.render;

import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.capture.FrameCaptures;
import com.igrium.replaylab.render.writer.FrameWriter;
import com.igrium.replaylab.render.writer.FrameWriterSpawner;
import com.igrium.replaylab.render.writer.FrameWriters;
import com.igrium.replaylab.scene.ReplayScene;
import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.core.mixin.MinecraftAccessor;
import com.replaymod.pathing.player.ReplayTimer;
import com.replaymod.render.hooks.ForceChunkLoadingHook;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.replay.ReplayFile;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.sound.SoundCategory;

import java.io.IOException;
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class VideoRenderer {

    public enum RenderState {
        STARTING,
        RENDERING,
        FINALIZING,
        FINISHED
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    private final ReplayScene scene;

    @Getter
    private final ReplayHandler replayHandler;

    @Getter
    private final VideoRenderSettings settings;

    @Getter
    private final int msPerFrame;

    @Getter
    private int currentFrame;

    @Getter
    private int totalFrames;

    private RenderScenePlayer scenePlayer;
    private CompletableFuture<?> scenePlayerFuture;
    private ForceChunkLoadingHook forceChunkLoadingHook;

    private boolean mouseWasGrabbed;
    private EnumMap<SoundCategory, Float> originalLevelSounds;

    private int sampleIdx;
    private int numSamples; // Number of samples to use for this frame.

    public static VideoRenderer open(ReplayScene scene, ReplayFile file, VideoRenderSettings settings) throws IOException {
        return new VideoRenderer(scene, ReplayModReplay.instance.startReplay(file, false, false), settings);
    }

    public VideoRenderer(ReplayScene scene, ReplayHandler replayHandler, VideoRenderSettings settings) {
        this.scene = scene;
        this.replayHandler = replayHandler;
        this.settings = settings;
        this.msPerFrame = (int) (1000 / settings.getFps());
        this.totalFrames = Math.ceilDiv(scene.getLength(), 1000);
    }

    public void render() throws VideoRenderException {
        RenderSystem.assertOnRenderThread();


        FrameCapture frameCapture = FrameCaptures.create(settings.getFrameCapture(), this);
        FrameWriter frameWriter = FrameWriters.create(settings.getFrameWriter(), this);

        if (!replayHandler.getReplaySender().isAsyncMode()) {
            throw new VideoRenderException("Replay handler is not async");
        }

        /// === SETUP ===
        scenePlayer = new RenderScenePlayer(replayHandler);
        scenePlayerFuture = scenePlayer.start(scene);

        // TODO: do we need this with CraftUI?
        mouseWasGrabbed = mc.mouse.isCursorLocked();
        mc.mouse.unlockCursor();

        originalLevelSounds = new EnumMap<>(SoundCategory.class);
        for (SoundCategory category : SoundCategory.values()) {
            if (category != SoundCategory.MASTER) {
                originalLevelSounds.put(category, mc.options.getSoundVolume(category));
                mc.options.getSoundVolumeOption(category).setValue(0d);
            }
        }

        try {
            frameWriter.begin();
        } catch (IOException e) {
            throw new VideoRenderException("Error initializing frame writer", e);
        }

        /// === RENDERING ===
        // Honestly I have no idea what replay mod is doing with all this mixin bullshit but I'm just copying it.
        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer();

        // Play up to one second before starting to render
        {
//            int videoStart = scene.getStartTime();
//            if (videoStart > 1000) {
//                int replayTime = videoStart - 1000;
//                timer.tickDelta = 0;
//
//                while (replayTime < videoStart) {
//                    replayTime += 50;
//                    replayHandler.getReplaySender().sendPacketsTill(replayTime);
//                    mc.tick();
//                }
//            }
        }

        // Just capture one frame for now for testing.
        NativeImage image = frameCapture.capture();
        frameWriter.write(image, 0, true);

        /// === FINISH ===

        try {
            frameWriter.finish().join();
        } catch (CompletionException e) {
            throw new VideoRenderException(e.getCause());
        } catch (Exception e) {
            throw new VideoRenderException(e);
        }

    }

    /**
     * Queue the next frame for rendering.
     *
     * @param numSamples The number of samples to be used rendering this frame.
     */
    public void queueNextFrame(int numSamples) {
        this.sampleIdx = 0;
        this.numSamples = numSamples;
    }

    /**
     * Queue the next sample for rendering.
     *
     * @return <code>true</code> if there's a next sample to render;
     * <code>false</code> if we're ready to move to the next frame.
     */
    public boolean queueSample() {
        if (sampleIdx < numSamples) {
            sampleIdx++;
            return true;
        }
        return false;
    }

    /**
     * Get the scene time a given frame should land on.
     * @param frameIdx Index of the frame.
     * @return The scene time in milliseconds.
     * @apiNote This is <em>not</em> replay time; this is the time on the timeline.
     */
    private int getSceneTime(int frameIdx) {
        return (int) Math.floor(getSceneTimeDouble(frameIdx));
    }

    private int getSceneTime() {
        return getSceneTime(currentFrame);
    }

    private double getSceneTimeDouble(int frameIdx) {
        return frameIdx * (1000d / settings.getFps());
    }

    private class RenderScenePlayer extends AbstractScenePlayer {

        public RenderScenePlayer(@NonNull ReplayHandler replayHandler) {
            super(replayHandler);
        }

        @Override
        public int getTimePassed() {
            return getSceneTime();
        }
    }
}
