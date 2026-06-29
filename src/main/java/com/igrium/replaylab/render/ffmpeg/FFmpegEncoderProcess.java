package com.igrium.replaylab.render.ffmpeg;

import com.google.common.collect.ImmutableList;
import com.igrium.replaylab.mixin.AccessorNativeImage;
import com.igrium.replaylab.mixin.AccessorRenderSettings;
import com.igrium.replaylab.render.ManagedNativeImage;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderException;
import com.igrium.replaylab.render.encoder.EncoderProcess;
import com.replaymod.render.FFmpegWriter;
import com.replaymod.render.utils.StreamPipe;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.CODECS;
import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.RateControlMode.CBR;
import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.RateControlMode.VBR;

/**
 * Adapted from {@link com.replaymod.render.FFmpegWriter} for use with ReplayLab scenes
 */
public class FFmpegEncoderProcess extends EncoderProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/FFmpegEncoderProcess");

    private final FFmpegEncoder encoderConfig;


    private Process process;
    /**
     * The output stream from THIS process to FFmpeg (confusing naming convention from Process)
     */
    private OutputStream outputStream;
    private String commandArgs;

    private WritableByteChannel channel;
    private final ByteArrayOutputStream ffmpegLog = new ByteArrayOutputStream(4096);

    public FFmpegEncoderProcess(FFmpegEncoder encoderConfig) {
        this.encoderConfig = encoderConfig;
    }

    protected void startEncoding() throws Exception {
        Files.createDirectories(getMetadata().outPath().getParent());

        Path exportLogFile = FabricLoader.getInstance().getGameDir().resolve("export.log");
        OutputStream exportLogOut = new TeeOutputStream(
                new BufferedOutputStream(Files.newOutputStream(exportLogFile)), ffmpegLog);

        File outFolder = getMetadata().outPath().getParent().toFile();

        List<String> cmd = generateCommand(getMetadata(), encoderConfig);
        LOGGER.info("FFmpeg command: {}", String.join(" ", cmd));

        try {
            process = new ProcessBuilder(cmd).directory(outFolder).start();
        } catch (IOException e) {
            throw new FFmpegWriter.NoFFmpegException(e);
        }

        new StreamPipe(process.getInputStream(), exportLogOut).start();
        new StreamPipe(process.getErrorStream(), exportLogOut).start();
        // Confusing name: getOutputStream returns an output stream from THIS process to FFmpeg.
        outputStream = process.getOutputStream();
        channel = Channels.newChannel(outputStream);
    }

    @Override
    protected void encodeFrame(ManagedNativeImage frame, int frameIdx) throws IOException {
        frame.useRawImage(nImg -> {
            AccessorNativeImage img = (AccessorNativeImage) (Object) nImg;
            assert img != null;
            // View of nativeimage memory
            ByteBuffer buffer = MemoryUtil.memByteBuffer(img.getPointer(), (int) img.getSizeBytes());
            channel.write(buffer);
        });
    }


    @Override
    protected CompletableFuture<?> finishEncoding() throws IOException {
        outputStream.close();
        return process.onExit().thenAccept(p -> {
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                throw new EncoderException("FFmpeg exited with code " + exitValue);
            }
        }).orTimeout(20, TimeUnit.SECONDS);
    }

    @Override
    protected void onFailed(Throwable reason) {
        try {
            outputStream.close();
        } catch (IOException e) {
            LOGGER.error("Error while closing output stream", e);
        }
        process.destroy();
    }

    public static List<String> generateCommand(RenderMetadata metadata, FFmpegEncoder encoderConfig) {
        var command = ImmutableList.<String>builder();
        command.add(AccessorRenderSettings.invokeFindFFmpeg());
        generateBoilerplateArgs(metadata, command);
        generateCodecArgs(encoderConfig, command);
        command.add(metadata.outPath().toString());

        return command.build();
    }

    public static void generateBoilerplateArgs(RenderMetadata meta, ImmutableList.Builder<String> builder) {
        builder.add("-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                "-s", meta.width() + "x" + meta.height(),
                "-r", String.valueOf(meta.fps()),
                "-i", "pipe:0");
    }

    /**
     * Generate the ffmpeg command line arguments that this encoder will use.
     */
    public static void generateCodecArgs(FFmpegEncoder encoderConfig, ImmutableList.Builder<String> args) {

        FFmpegEncoder.CodecType codec = CODECS.get(encoderConfig.getCodec());
        if (codec == null) {
            throw new IllegalArgumentException("Unknown codec: " + encoderConfig.getCodec());
        }
        String encoder = codec.encoderName();

        args.add("-c:v", encoder);

        if (codec.supportsBitrate()) {
            args.add("-preset", encoderConfig.getEncPreset().name().toLowerCase());

            switch (encoderConfig.getRcMode()) {
                case CRF -> {
                    args.add("-crf", String.valueOf(encoderConfig.getCrfValue()));

                    // VP8/VP9 require explicitly setting bitrate to 0 to unlock pure Constant Quality
                    if (codec.family() == FFmpegEncoder.CodecFamily.VPX) {
                        args.add("-b:v", "0");
                    }
                }
                case VBR -> args.add("-b:v", encoderConfig.getBitrate() + "k");
                case CBR -> {
                    String br = encoderConfig.getBitrate() + "k";
                    // Constant Bitrate implementations vary by encoder library
                    if (codec.family() == FFmpegEncoder.CodecFamily.VPX) {
                        args.add("-b:v", br,
                                "-maxrate", br,
                                "-minrate", br);
                    } else {
                        args.add("-b:v", br,
                                "-maxrate", br,
                                "-bufsize", String.valueOf(encoderConfig.getBitrate() * 2));
                    }
                }
            }
        } else {
            // Hardcoded fallback profiles for non-bitrate intermediate codecs
            args.add("-profile:v", codec.profile());
        }
    }

    private void checkSize(int width, int height) {
        if (width != getMetadata().width()) {
            throw new IllegalArgumentException("Frame width != video width");
        }
        if (height != getMetadata().height()) {
            throw new IllegalArgumentException("Frame height != video height");
        }
    }
}
