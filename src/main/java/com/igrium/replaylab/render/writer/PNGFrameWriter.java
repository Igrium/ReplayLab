package com.igrium.replaylab.render.writer;

import com.igrium.replaylab.render.VideoRenderer;
import net.minecraft.client.texture.NativeImage;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Saves frames into PNG files. Lossless, but slow and CPU-intensive.
 */
public class PNGFrameWriter implements FrameWriter {
    private final Set<CompletableFuture<?>> futures = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final VideoRenderer renderer;

    private ExecutorService executorService;

    public PNGFrameWriter(VideoRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void begin() throws IOException {
        Files.createDirectories(renderer.getSettings().getOutputFile());
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> new Thread(r, "PNG Encoder"));
    }

    @Override
    public void write(NativeImage image, int frameIdx, boolean autoFree) {
        var future = CompletableFuture.runAsync(() -> {
            Path outPath = renderer.getSettings().getOutputFile().resolve(frameIdx + ".png");
            try {
                image.writeTo(outPath);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            } finally {
                if (autoFree) {
                    image.close();
                }
            }
        }, executorService);
        futures.add(future);
    }

    @Override
    public CompletableFuture<?> finish() {
        executorService.shutdown();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}
