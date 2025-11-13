package com.igrium.replaylab.render.writer;

import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;
import lombok.Getter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Util;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class PNGFrameWriter implements FrameWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/PNGFrameWriter");

    private final VideoRenderSettings settings;

    @Getter
    private volatile @Nullable Throwable error;

    private volatile @Nullable CompletableFuture<?> finishFuture;

    public PNGFrameWriter(VideoRenderer renderer, VideoRenderSettings settings) {
        this.settings = settings;
    }

    private ExecutorService pngWriterService;

    @Override
    public void start() throws Exception {
        pngWriterService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1,
                r -> new Thread(r, "PNG Encoding Thread"));
    }

    @Override
    public void write(NativeImage image, int frameIdx) {
        if (error != null)
            return;
        pngWriterService.submit(() -> {
            if (error != null)
                return;

            var path = settings.getOutPath().resolve("." + frameIdx);

            try {
                image.writeTo(path);
                image.close();
            } catch (Throwable e) {
                LOGGER.error("Error saving PNG frame {}", frameIdx, error);
                error = e;
            }
        });
    }

    @Override
    public CompletableFuture<?> finish() {
        if (finishFuture != null) {
            return finishFuture;
        }

        finishFuture = CompletableFuture.runAsync(() -> {
            try {
                if (!pngWriterService.awaitTermination(60, TimeUnit.SECONDS)) {
                    throw new TimeoutException("PNG writer timed out");
                }
            } catch (Exception e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        }, Util.getIoWorkerExecutor()).thenRun(() -> {
            if (error != null) {
                throw new CompletionException(error);
            }
        });
        return finishFuture;
    }

}
