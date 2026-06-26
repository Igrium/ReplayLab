package com.igrium.replaylab.render.ffmpeg;

import com.igrium.replaylab.render.ManagedNativeImage;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.encoder.EncoderProcess;

import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class FFmpegEncoderProcess extends EncoderProcess {

    private Process process;
    private OutputStream outputStream;
    private String commandArgs;

    @Override
    protected void startEncoding(RenderMetadata metadata) throws Exception {
        String fileName = metadata.outPath().getFileName().toString();
        Files.createDirectories(metadata.outPath().getParent());


    }

    @Override
    protected void encodeFrame(ManagedNativeImage frame, int frameIdx) throws Exception {

    }

    @Override
    protected CompletableFuture<?> finishEncoding() throws Exception {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void onFailed(Throwable reason) {

    }
}
