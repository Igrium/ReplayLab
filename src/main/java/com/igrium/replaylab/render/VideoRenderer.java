package com.igrium.replaylab.render;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.ObjectRenderSettings;
import com.igrium.replaylab.object.ObjectSceneProps;
import com.igrium.replaylab.playback.AbstractScenePlayer;
import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderProcess;
import com.igrium.replaylab.scene.ReplayScene;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.replaymod.core.mixin.BlockableEventLoopAccessor;
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
import net.minecraft.ReportedException;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VideoRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/VideoRenderer");

    /** Per-attempt wait for the frame readback fence. Looped, so a slow frame just waits again. */
    private static final long FENCE_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(5);

    @Getter
    private static boolean renderingVideo;

    public enum RenderState {
        READY,
        STARTING,
        RENDERING,
        FINISHING,
        DONE
    }

    private final Minecraft mc = Minecraft.getInstance();

    @Getter
    private final RenderMetadata renderMetadata;

    @Getter
    private final ReplayHandler replay;

    @Getter
    private final ReplayScene scene;

    private @Nullable VirtualWindow guiWindow;

    @Getter
    private final FrameCapture frameCapture;

    @Getter
    private final EncoderConfig encoder;

    @Getter
    private int frameIdx = 0;

    public int getTotalFrames() {
        return renderMetadata.totalFrames();
    }

    @Getter
    private RenderState renderState = RenderState.READY;

    private volatile boolean abort;

    /**
     * The texture currently being rendered to. <code>null</code> if we're not rendering.
     */
    @Getter
    private @Nullable SimpleTexture renderTexture;

    /**
     * Persistent buffer for reading from gpu into cpu.
     */
    private @Nullable GpuBuffer readbackBuffer;

    public VideoRenderer(RenderMetadata renderMetadata, ReplayHandler replay, ReplayScene scene, FrameCapture frameCapture, EncoderConfig encoder) {
        this.renderMetadata = renderMetadata;
        this.replay = replay;
        this.scene = scene;
        this.frameCapture = frameCapture;
        this.encoder = encoder;
    }

    public static VideoRenderer create(ReplayScene scene) {
        ReplayHandler replayHandler = EditorState.getReplayHandlerOrThrow();
        ObjectSceneProps sceneProps = scene.getSceneProps();
        ObjectRenderSettings renderSettings = scene.getRenderSettings();

        int totalFrames = (int) (sceneProps.getLength() * sceneProps.getFps() / 1000);

        RenderMetadata metadata = RenderMetadata.builder()
                .outPath(renderSettings.getOutPath())
                .width(sceneProps.getResolutionX())
                .height(sceneProps.getResolutionY())
                .fps(sceneProps.getFps())
                .totalFrames(totalFrames)
                .build();

        return new VideoRenderer(metadata, replayHandler, scene, renderSettings.getFrameCapture(), renderSettings.getEncoder());
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

        // 26.2 moved the debug overlay's visibility flag onto DebugScreenEntryList.
        boolean debugWasShown = mc.debugEntries.isOverlayVisible();
        boolean mouseHandlerWasGrabbed = mc.mouseHandler.isMouseGrabbed();
        EnumMap<SoundSource, Float> originalSoundLevels = new EnumMap<>(SoundSource.class);
        ForceChunkLoadingHook forceChunkLoadingHook = null;

        RenderScenePlayer scenePlayer = null;
        try {
            /// === SETUP ===

            frameCapture.setMetadata(renderMetadata);

            EncoderProcess encoder = getEncoder().spawnEncoder();

            scenePlayer = new RenderScenePlayer(replay);
            scenePlayer.start(scene);

            if (debugWasShown) {
                mc.debugEntries.setOverlayVisible(false);
            }


            int[] realWidth = new int[1];
            int[] realHeight = new int[1];
            GLFW.glfwGetFramebufferSize(mc.getWindow().handle(), realWidth, realHeight);
            MCVer.resizeMainWindow(mc, realWidth[0], realHeight[0]);

            guiWindow = new VirtualWindow(mc);

            mc.mouseHandler.releaseMouse();

            for (var category : SoundSource.values()) {
                if (category != SoundSource.MASTER) {
                    originalSoundLevels.put(category, mc.options.getSoundSourceVolume(category));
                    mc.options.getSoundSourceOptionInstance(category).set(0d);
                }
            }

            forceChunkLoadingHook = new ForceChunkLoadingHook(mc.levelRenderer);

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
            renderTexture = frameCapture.generateTexture();

            renderState = RenderState.RENDERING;
            while (frameIdx < renderMetadata.totalFrames() && !abort) {
                if (GLFW.glfwWindowShouldClose(mc.getWindow().handle()) || getDelayedCrash() != null) {
                    encoder.finish().get(10, TimeUnit.SECONDS);
                }
                int curIdx = frameIdx;
                queueFrame(frameIdx, 1);
                frameCapture.captureFrame(curIdx, renderTexture);

                // TODO: if the texture is downloaded async under-the-hood,
                // can we write it async as well?
                NativeImage nImage = downloadTexture(renderTexture);

                drawGui();

                Throwable e = encoder.getFailureReason();
                if (e != null) {
                    nImage.close();
                    throw (Exception) e;
                }
                // Ownership passes to the encoder, which closes it once the frame is written.
                encoder.accept(nImage, curIdx);
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

            if (getDelayedCrash() != null) {
                throw new ReportedException(getDelayedCrash().get());
            }

            // TODO: spherical metadata


            return !abort;
        } finally {
            /// === CLEANUP ===

            renderingVideo = false;
            renderState = RenderState.DONE;

            if (renderTexture != null) {
                renderTexture.close();
                renderTexture = null;
            }

            if (readbackBuffer != null) {
                readbackBuffer.close();
                readbackBuffer = null;
            }


            if (scenePlayer != null) {
                scenePlayer.stop();
            }

            if (debugWasShown) {
                mc.debugEntries.setOverlayVisible(true);
            }

            if (mouseHandlerWasGrabbed) {
                mc.mouseHandler.grabMouse();
            }

            for (var entry : originalSoundLevels.entrySet()) {
                mc.options.getSoundSourceOptionInstance(entry.getKey()).set(Double.valueOf(entry.getValue()));
            }

            mc.gui.setScreen(null);
            if (forceChunkLoadingHook != null) {
                forceChunkLoadingHook.uninstall();
            }

            var event = SoundEvent.createFixedRangeEvent(Identifier.parse("replaymod:render_success"), 1);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1));

            // Finally, resize the Minecraft framebuffer to the actual width/height of the window

            if (guiWindow != null) {
                guiWindow.close();
                MCVer.resizeMainWindow(mc, guiWindow.getFramebufferWidth(), guiWindow.getFramebufferHeight());
            }
        }
    }

    private @Nullable java.util.function.Supplier<net.minecraft.CrashReport> getDelayedCrash() {
        return ((BlockableEventLoopAccessor) mc).getDelayedCrash();
    }

    /**
     * Read a rendered frame back off the GPU into a {@link NativeImage}.
     * <p>
     * The copy is asynchronous on both backends -- GL turns it into a PBO {@code glReadPixels} and
     * defers the callback to a fence, Vulkan records it into the in-flight command buffer -- so we
     * must block on a fence before mapping. Mapping straight away yields a partially written buffer,
     * which shows up as the bottom of the frame being cut off by a different amount each time.
     * Vanilla's {@link net.minecraft.client.Screenshot} sidesteps this by reading inside the
     * callback; the export loop needs the pixels synchronously, so it waits instead.
     * <p>
     * Minecraft's offscreen render target leaves alpha at 0, so alpha is forced opaque here. The
     * vertical flip that {@code NativeImage.flipY} used to do is folded into the row indexing.
     */
    private NativeImage downloadTexture(SimpleTexture texture) {
        RenderSystem.assertOnRenderThread();

        int width = texture.getWidth();
        int height = texture.getHeight();
        GpuTexture gpuTexture = texture.getTexture();
        int blockSize = gpuTexture.getFormat().blockSize();

        // Kept for the whole export rather than allocated per frame; it's 32 MiB at 4K.
        if (readbackBuffer == null) {
            readbackBuffer = RenderSystem.getDevice().createBuffer(() -> "ReplayLab frame readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * blockSize);
        }
        GpuBuffer buffer = readbackBuffer;

        NativeImage image = new NativeImage(width, height, false);
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToBuffer(gpuTexture, buffer, 0, () -> {}, 0);

            // The fence has to be created before the submit: Vulkan's implementation pins it to the
            // submission that is currently being built, which is the one holding the copy.
            try (GpuFence fence = encoder.createFence()) {
                encoder.submit();
                //noinspection StatementWithEmptyBody
                while (!fence.awaitCompletion(FENCE_TIMEOUT_NS)) {}
            }

            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = view.data().getInt((x + y * width) * blockSize);
                        image.setPixelABGR(x, height - y - 1, argb | 0xFF000000);
                    }
                }
            }
        } catch (Throwable t) {
            image.close();
            throw t;
        }

        return image;
    }

    public float queueFrame(int sampleIdx, int totalSamples) {
        guiWindow.bind();

        // Updating the timer will cause the timeline player to update the game state
        ReplayTimer timer = (ReplayTimer) ((MinecraftAccessor) mc).getTimer();
        try {
            // TODO: GUI update
            int elapsedTicks = timer.advanceGameTime(Util.getMillis());
            executeTaskQueue();

            while (elapsedTicks-- > 0) {
                mc.tick();
            }

            // 26.1+ moved per-frame level bookkeeping out of tick() and into Level.update()
            if (mc.level != null) {
                mc.level.update();
            }
        } finally {
            guiWindow.unbind();
        }

        // TODO: camera path exporter
        scene.spectateCamera();
        frameIdx++;

        return timer.tickDelta;
    }

    private void executeTaskQueue() {
        while (true) {
            while (mc.gui.overlay() != null) {
                ((MCVer.MinecraftMethodAccessor) mc).replayModExecuteTaskQueue();
            }

            // I'll be real, I don't really know what this is doing but I'll copy it
            CompletableFuture<Void> resourceReloadFuture = ((MinecraftAccessor) mc).getResourceReloadFuture();
            if (resourceReloadFuture != null) {
                ((MinecraftAccessor) mc).setResourceReloadFuture(null);
                mc.reloadResourcePacks().thenRun(() -> resourceReloadFuture.complete(null));
                continue;
            }
            break;
        }

        ((MCVer.MinecraftMethodAccessor) mc).replayModExecuteTaskQueue();
    }

    public boolean drawGui() {
        Window window = mc.getWindow();
        if (GLFW.glfwWindowShouldClose(window.handle()) || getDelayedCrash() != null) {
            return false;
        }

        // Minecraft's main loop isn't running during export; without this the OS window stops responding.
        RenderSystem.pollEvents();

        // captureFrame leaves Window sized to the video resolution. The progress UI is drawn at the
        // window's real size (ImGui takes its display size straight from GLFW), so bind the virtual
        // window for the duration -- otherwise everything is laid out against the video resolution.
        guiWindow.bind();
        try {
            drawGuiBound();
        } finally {
            guiWindow.unbind();
        }

        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        return !abort;
    }

    private void drawGuiBound() {
        Window window = mc.getWindow();

        clearMainRenderTarget();

        // While the gui window is bound for writing, mc.gameRenderer.mainRenderTarget() resolves to
        // ReplayMod's gui framebuffer -- which is what CraftUI's ImGui backend draws into.
        guiWindow.beginWrite();
        try {
            clearMainRenderTarget();

            WindowRenderState windowRenderState = mc.gameRenderer.gameRenderState().windowRenderState;
            windowRenderState.width = window.getWidth();
            windowRenderState.height = window.getHeight();
            windowRenderState.guiScale = window.getGuiScale();
            windowRenderState.appropriateLineWidth = window.getAppropriateLineWidth();
            windowRenderState.isMinimized = window.isMinimized();

            // The progress UI is drawn entirely by CraftUI/ImGui, which writes straight into
            // mainRenderTarget -- nothing goes through the vanilla gui render state here.
            AppManager.render(mc);
        } finally {
            guiWindow.endWrite();
        }

        // Replaces Window.updateDisplay: presents the gui framebuffer through the window surface.
        guiWindow.flip();
    }

    private void clearMainRenderTarget() {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), new Vector4f(), target.getDepthTexture(), 0);
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
