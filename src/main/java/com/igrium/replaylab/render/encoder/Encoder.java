package com.igrium.replaylab.render.encoder;

import com.google.gson.JsonObject;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.ManagedNativeImage;
import com.igrium.replaylab.render.RenderMetadata;
import imgui.ImGui;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class Encoder {

    private static final Logger LOGGER = ReplayLab.getLogger(Encoder.class);

    public enum EncodingState {
        READY,
        ENCODING,
        FINALIZING,
        FINISHED,
        FAILED
    }

    @Getter
    private final EncoderType<?> type;

    @Getter
    private volatile EncodingState state = EncodingState.READY;

    private final AtomicReference<Throwable> failureReason = new AtomicReference<>();

    public @Nullable Throwable getFailureReason() {
        return failureReason.get();
    }

    /**
     * The path we're currently encoding to.
     */
    @Getter
    private RenderMetadata metadata = RenderMetadata.builder().build();

    public Encoder(EncoderType<?> type) {
        this.type = type;
    }

    public final JsonObject saveConfig(JsonObject dest) {
        return getType().save(this, dest);
    }

    /**
     * Mark this encoder as having failed. Useful for exceptions raised out-of-thread.
     * @param failureReason The reason for the failure.
     */
    protected final void fail(Throwable failureReason) {
         if (this.failureReason.compareAndSet(null, failureReason)) {
             state = EncodingState.FAILED;
             onFailed(failureReason);
         }
    }

    /**
     * Initialize and begin encoding
     * @param metadata Encoding metadata
     * @throws IllegalStateException If the encoder isn't ready to start
     */
    public synchronized void start(RenderMetadata metadata) throws IllegalStateException {
        if (state == EncodingState.ENCODING || state == EncodingState.FINISHED) {
            throw new IllegalStateException("Encoder is not ready to start! (Current state: " + state + ")");
        }

        this.metadata = metadata;
        failureReason.set(null);
        state = EncodingState.ENCODING;

        try {
            startEncoding(metadata);
        } catch (Exception e) {
            LOGGER.error("Error starting encoder!", e);
            fail(e);
            throw new EncoderException(e);
        }
    }

    protected abstract void startEncoding(RenderMetadata metadata) throws Exception;

    /**
     * Queue a frame to be encoded.
     * @param frame The frame to encode.
     * @throws IllegalStateException If we're not in state {@link EncodingState#ENCODING}
     * @apiNote If the encoder is not ready to receive the frame (buffer is full, etc.), blocks until it's ready
     */
    public final void accept(ManagedNativeImage frame, int frameIdx) throws IllegalStateException, EncoderException {
        ensureNotFailed();
        if (state != EncodingState.ENCODING) {
            throw new IllegalStateException("Encoder must be in EncodingState.ENCODING (was " + state + ")");
        }

        try {
            encodeFrame(frame, frameIdx);
        } catch (Exception e) {
            LOGGER.error("Encoding failed!", e);
            fail(e);
            throw new EncoderException(e);
        }
    }

    protected abstract void encodeFrame(ManagedNativeImage frame, int frameIdx) throws Exception;

    /**
     * Asynchronously finalize this encoder.
     * @return A future that completes once the file has been fully written to.
     */
    public synchronized final CompletableFuture<?> finish() {
        ensureNotFailed();
        if (state != EncodingState.ENCODING) {
            throw new IllegalStateException("Encoder must be in EncodingState.ENCODING (was " + state + ")");
        }

        state = EncodingState.FINALIZING;

        CompletableFuture<?> future;
        try {
            future = finishEncoding();
        } catch (Exception e) {
            future = CompletableFuture.failedFuture(e);
        }

        return future.thenApply(v -> {
                    state = EncodingState.FINISHED;
                    return v;
                })
                .exceptionally(e -> {
                    if (e instanceof CompletionException) e = e.getCause();

                    LOGGER.error("Error finalizing encoder!", e);
                    fail(e);
                    throw new EncoderException(e);
                });
    }

    protected abstract CompletableFuture<?> finishEncoding() throws Exception;

    protected abstract void onFailed(Throwable reason);

    public void drawProperties(EditorState editorState) {
        ImGui.text("This encoder has no configurable properties.");
    }

    private void ensureNotFailed() throws EncoderException {
        var cause = failureReason.get();
        if (cause != null) {
            throw  new EncoderException(cause);
        }
    }
}