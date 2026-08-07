package com.igrium.replaylab.render.encoder;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.mojang.blaze3d.platform.NativeImage;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.util.SimpleBlockingQueue;
import net.minecraft.util.Util;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.nio.file.Files;
import java.util.concurrent.*;

public class PNGEncoder extends EncoderConfig {
    protected PNGEncoder(EncoderType<?> type) {
        super(type);
    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {

    }

    @Override
    public JsonObject writeJson(JsonSerializationContext context) {
        return new JsonObject();
    }

    @Override
    public PNGEncoderProcess spawnEncoder() {
        return new PNGEncoderProcess();
    }

    @Override
    public boolean wantsDirectory() {
        return true;
    }

    /**
     * An encoder that simply writes PNG files.
     */
    public static class PNGEncoderProcess extends EncoderProcess {

        /**
         * Rough ceiling on how much frame data may sit in the write queue at once.
         */
        private static final long MAX_QUEUED_BYTES = 512L * 1024 * 1024;

        private ExecutorService executor;


        @Override
        protected void startEncoding() throws Exception {
            Files.createDirectories(getMetadata().outPath());
            int availableProcessors = Runtime.getRuntime().availableProcessors();

            // Bound queue by bytes so it scales cleanly with resolution (and therefore memory usage)
            long frameBytes = (long) getMetadata().width() * getMetadata().height() * 4;
            int queueSize = Math.clamp(MAX_QUEUED_BYTES / Math.max(frameBytes, 1), 4, 32);

            executor = new ThreadPoolExecutor(
                    availableProcessors,
                    availableProcessors,
                    20,
                    TimeUnit.MILLISECONDS,
                    new SimpleBlockingQueue<>(queueSize)
            );
        }

        @Override
        protected void encodeFrame(NativeImage frame, int frameIdx) {
            try {
                executor.submit(() -> {
                    try {
                        if (getState() != EncodingState.ENCODING)
                            return;

                        int maxDigits = (int) (Math.log10(getMetadata().totalFrames()) + 1);
                        String prefix = String.format("%0" + maxDigits + "d", frameIdx);
                        var path = getMetadata().outPath().resolve(prefix + ".png");
                        frame.writeToFile(path);
                    } catch (Exception e) {
                        fail(e);
                    } finally {
                        frame.close();
                    }
                });
            } catch (RejectedExecutionException e) {
                // Nobody took ownership, so it's still ours to free.
                frame.close();
                throw e;
            }
        }

        @Override
        protected CompletableFuture<?> finishEncoding() {
            executor.shutdown();
            // Because awaitTermination is blocking and doesn't offer a future-based alternative,
            // we need to do this. It's dumb.
            return CompletableFuture.runAsync(() -> {
                try {
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        throw new TimeoutException("PNG writer timed out.");
                    }
                } catch (InterruptedException | TimeoutException e) {
                    // There's absolutely no reason runAsync shouldn't handle checked exceptions
                    throw ExceptionUtils.asRuntimeException(e);
                }
            }, Util.ioPool());
        }

        @Override
        protected void onFailed(Throwable reason) {
            executor.shutdownNow();
        }
    }
}
