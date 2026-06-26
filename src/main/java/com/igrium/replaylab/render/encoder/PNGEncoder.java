package com.igrium.replaylab.render.encoder;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.render.ManagedNativeImage;
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

    /**
     * An encoder that simply writes PNG files.
     */
    public static class PNGEncoderProcess extends EncoderProcess {

        private ExecutorService executor;


        @Override
        protected void startEncoding(RenderMetadata metadata) throws Exception {
            Files.createDirectories(metadata.outPath());
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            executor = new ThreadPoolExecutor(
                    availableProcessors,
                    availableProcessors,
                    20,
                    TimeUnit.MILLISECONDS,
                    new SimpleBlockingQueue<>(32)
            );
        }

        @Override
        protected void encodeFrame(ManagedNativeImage frame, int frameIdx) {
            executor.submit(() -> {
                try {
                    if (getState() != EncodingState.ENCODING)
                        return;

                    int maxDigits = (int) (Math.log10(getMetadata().totalFrames()) + 1);
                    String prefix = String.format("%0" + maxDigits + "d", frameIdx);
                    var path = getMetadata().outPath().resolve(prefix + ".png");
                    frame.writeTo(path);
                } catch (Exception e) {
                    fail(e);
                }
            });
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
            }, Util.getIoWorkerExecutor());
        }

        @Override
        protected void onFailed(Throwable reason) {
            executor.shutdownNow();
        }
    }
}
