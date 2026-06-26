package com.igrium.replaylab.render.ffmpeg;

import com.google.common.collect.ImmutableList;
import com.igrium.replaylab.mixin.AccessorRenderSettings;
import com.igrium.replaylab.render.ManagedNativeImage;
import com.igrium.replaylab.render.RenderMetadata;
import com.igrium.replaylab.render.encoder.EncoderProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.CODECS;
import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.RateControlMode.CBR;
import static com.igrium.replaylab.render.ffmpeg.FFmpegEncoder.RateControlMode.VBR;

public class FFmpegEncoderProcess extends EncoderProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/FFmpegEncoderProcess");

    private Process process;
    private OutputStream outputStream;
    private String commandArgs;

    private final FFmpegEncoder encoderConfig;

    public FFmpegEncoderProcess(FFmpegEncoder encoderConfig) {
        this.encoderConfig = encoderConfig;
    }

    protected void startEncoding() throws Exception {
        Files.createDirectories(getMetadata().outPath().getParent());
        LOGGER.info("FFmpeg command: {}", String.join(" ", generateCommand()));
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

    public List<String> generateCommand() {
        var command = ImmutableList.<String>builder();
        command.add(AccessorRenderSettings.invokeFindFFmpeg());
        generateBoilerplateArgs(command);
        generateCodecArgs(command);
        command.add(getMetadata().outPath().toString());

        return command.build();
    }

    public void generateBoilerplateArgs(ImmutableList.Builder<String> builder) {
        RenderMetadata meta = getMetadata();

        builder.add("-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
                "-s", meta.width() + "x" + meta.height(),
                "-r", String.valueOf(meta.fps()),
                "-i", "pipe:0");
    }

    /**
     * Generate the ffmpeg command line arguments that this encoder will use.
     */
    public void generateCodecArgs(ImmutableList.Builder<String> args) {

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
}
