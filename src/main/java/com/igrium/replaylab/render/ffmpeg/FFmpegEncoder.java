package com.igrium.replaylab.render.ffmpeg;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderProcess;
import com.igrium.replaylab.render.encoder.EncoderType;
import com.igrium.replaylab.ui.NoFFmpegPopup;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public class FFmpegEncoder extends EncoderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/FFmpegEncoder");

    public enum CodecFamily {
        X264, AV1, VPX, PRO
    }

    public record ContainerType(String[] extensions, String... codecs) {
        public ContainerType(String extension, String... supportedCodecs) {
            this(new String[]{extension}, supportedCodecs);
        }

        public boolean isExtSupported(String ext) {
            return extensions.length == 0 || arrayContains(extensions, ext);
        }

        public boolean isCodecSupported(String codec) {
            return codecs.length == 0 || arrayContains(codecs, codec);
        }

    }

    public record CodecType(String encoderName, boolean supportsBitrate, CodecFamily family, String profile) {
        public CodecType(String encoderName, boolean supportsBitrate, CodecFamily family) {
            this(encoderName, supportsBitrate, family, "");
        }
    }


    public enum RateControlMode {
        CBR, VBR, CRF;

        public String langKey() {
            return "ratecontrol." + name().toLowerCase();
        }
    }

    public enum BitratePreset {

        BR_2_5(2_500),
        BR_5(5_000),
        BR_10(10_000),
        BR_15(15_000),
        BR_20(20_000);

        final int bitrate;

        BitratePreset(int bitrate) {
            this.bitrate = bitrate;
        }

        public String langKey() {
            return "bitrate." + name().toLowerCase();
        }
    }

    public enum EncodingPreset {
        ULTRAFAST(13, "realtime", 12),
        SUPERFAST(12, "realtime", 8),
        VERYFAST(11, "realtime", 5),
        FASTER(9, "good", 4),
        FAST(8, "good", 3),
        MEDIUM(7, "good", 2),
        SLOW(5, "good", 1),
        SLOWER(3, "good", 0),
        VERYSLOW(1, "best", 0);

        @Getter
        final int av1;

        @Getter
        final String vpxDeadline;

        @Getter
        final int vpxCpu;

        EncodingPreset(int av1, String vpxDeadline, int vpxCpu) {
            this.av1 = av1;
            this.vpxDeadline = vpxDeadline;
            this.vpxCpu = vpxCpu;
        }

        public String langKey() {
            return "encpreset." + name().toLowerCase();
        }
    }

    public static final Map<String, ContainerType> CONTAINERS = ImmutableMap.of(
            "mp4", new ContainerType("mp4", "h.264", "h.265", "av1"),
            "mkv", new ContainerType("mkv", "h.264", "h.265", "av1", "vp8", "vp9", "prores", "dnxhr"),
            "mov", new ContainerType("mov", "h.264", "h.265", "prores", "dnhxr"),
            "webm", new ContainerType("webm", "vp8", "vp9", "av1")
    );


    // TODO: multiple prores versions
    public static final Map<String, CodecType> CODECS = ImmutableMap.of(
            "h.264", new CodecType("libx264", true, CodecFamily.X264),
            "h.265", new CodecType("libx265", true, CodecFamily.X264),
            "av1", new CodecType("libsvtav1", true, CodecFamily.AV1),
            "vp8", new CodecType("libvpx", true, CodecFamily.VPX),
            "vp9", new CodecType("libvpx-vp9", true, CodecFamily.VPX),
            "prores", new CodecType("prores_ks", false, CodecFamily.PRO, "1"), // ProRes 422 LT
            "dnxhr", new CodecType("dnxhd", false, CodecFamily.PRO, "dnxhr_hq")
    );

    private @Nullable Boolean hasFFmpeg;

    public boolean hasFFmpeg() {
        if (hasFFmpeg == null) {
            hasFFmpeg = FFmpegEncoderProcess.hasFFmpeg();
        }
        return hasFFmpeg;
    }

    @Getter
    @NonNull
    private String container = "mp4";

    public void setContainer(@NonNull String container) {
        this.container = container;
        ContainerType cType = CONTAINERS.get(this.container);
        if (cType != null && !cType.isCodecSupported(getCodec())) {
            setCodec(cType.codecs[0]);
        }
    }

    @Getter
    @Setter
    @NonNull
    private String codec = "h.264";

    @Getter
    @Setter
    private EncodingPreset encPreset = EncodingPreset.MEDIUM;

    @Getter
    @Setter
    @NonNull
    private RateControlMode rcMode = RateControlMode.VBR;

    @Getter
    @Setter
    private int crfValue = 23;

    @Getter
    @Nullable
    private BitratePreset bitratePreset = BitratePreset.BR_15;

    public void setBitratePreset(@Nullable BitratePreset bitratePreset) {
        this.bitratePreset = bitratePreset;
        if (bitratePreset != null) {
            setBitrate(bitratePreset.bitrate);
        }
    }

    /**
     * Bitrate in kbps
     */
    @Getter
    private int bitrate = 15_000;

    public void setBitrate(int bitrate) {
        this.bitrate = Math.clamp(bitrate, 0, 100_000);
    }

    @Getter
    @Setter    @NonNull
    private String customArgs = "";

    public FFmpegEncoder(EncoderType<?> type) {
        super(type);
    }

    public void copyFrom(FFmpegEncoder other) {
        this.container = other.container;
        this.codec = other.codec;
        this.encPreset = other.encPreset;
        this.rcMode = other.rcMode;
        this.crfValue = other.crfValue;
        this.bitrate = other.bitrate;
    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {
        copyFrom(context.deserialize(json, FFmpegEncoder.class));
    }

    @Override
    public JsonObject writeJson(JsonSerializationContext context) {
        return context.serialize(this).getAsJsonObject();
    }

    @Override
    public FFmpegEncoderProcess spawnEncoder() {
        return new FFmpegEncoderProcess(this);
    }

    private static String containerLabel(String ext) {
        return tt("container." + ext) + " (." + ext + ")";
    }

    @Override
    public void drawProperties(EditorState editor) {
        if (!hasFFmpeg()) {
            NoFFmpegPopup.render();
            return;
        }

        if (ImGui.beginCombo(t("gui.replaylab.ffmpeg.container"), containerLabel(container))) {
            for (var c : CONTAINERS.keySet()) {
                boolean selected = c.equals(this.container);
                if (drawComboItem(containerLabel(c), selected)) {
                    setContainer(c);
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.beginCombo(t("gui.replaylab.ffmpeg.codec"), t("codec." + codec))) {
            for (var c : CODECS.keySet()) {
                ImGui.beginDisabled(!isCodecAllowed(c));

                if (drawComboItem(t("codec." + c), c.equals(this.codec))) {
                    setCodec(c);
                }

                ImGui.endDisabled();
            }
            ImGui.endCombo();
        }

        ImGui.separator();

        var selCodec = CODECS.get(this.codec);
        ImGui.beginDisabled(!selCodec.supportsBitrate);

        if (ImGui.beginCombo(t("gui.replaylab.ffmpeg.rate_control"), t(rcMode.langKey()))) {
            for (var c : RateControlMode.values()) {
                boolean selected = c.equals(this.rcMode);
                if (ImGui.selectable(t(c.langKey()), selected)) {
                    setRcMode(c);
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        if (this.rcMode == RateControlMode.CRF) {
            int[] crfIn = {crfValue};
            if (ImGui.dragScalar(t("gui.replaylab.ffmpeg.crf"), crfIn, .25f, 0, 63)) {
                setCrfValue(crfIn[0]);
            }
            ImGui.setItemTooltip(tt("gui.replaylab.ffmpeg.crf.tooltip"));

        } else {
            String label = bitratePreset != null ? bitratePreset.langKey() : "bitrate.custom";
            if (ImGui.beginCombo(t("gui.replaylab.ffmpeg.quality"), tt(label))) {
                for (var preset : BitratePreset.values()) {
                    if (drawComboItem(t(preset.langKey()), preset == this.bitratePreset)) {
                        setBitratePreset(preset);
                    }
                }
                if (drawComboItem(t("bitrate.custom"), this.bitratePreset == null)) {
                    setBitratePreset(null);
                }

                ImGui.endCombo();
            }

            if (bitratePreset == null) {
                ImInt brInput = new ImInt(bitrate);
                if (ImGui.inputInt(t("gui.replaylab.ffmpeg.bitrate_val"), brInput, 1000, 5000)) {
                    setBitrate(brInput.get());
                }
            }
        }

        ImGui.endDisabled();
        ImGui.separator();

        if (ImGui.beginCombo(t("gui.replaylab.ffmpeg.preset"), t(encPreset.langKey()))) {
            for (var preset : EncodingPreset.values()) {
                if (drawComboItem(t(preset.langKey()), preset == this.encPreset)) {
                    setEncPreset(preset);
                }
            }
            ImGui.endCombo();
        }

        ImGui.setItemTooltip(tt("gui.replaylab.ffmpeg.preset.tooltip"));
    }

    @Override
    public boolean mayExport() {
        return hasFFmpeg();
    }

    private static boolean drawComboItem(String label, boolean selected) {
        boolean success = ImGui.selectable(label, selected);
        if (selected) {
            ImGui.setItemDefaultFocus();
        }
        return success;
    }

    private boolean isCodecAllowed(String codec) {
        var type = CONTAINERS.get(container);
        return type == null || type.isCodecSupported(codec);
    }

    @Override
    public String[] getSupportedExtensions() {
        ContainerType type = CONTAINERS.get(container);
        return type != null ? type.extensions() : new String[0];
    }

    @Override
    public String getFileFilterName() {
        return tt("container." + container);
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static <T> boolean arrayContains(T[] array, T value) {
        for (T val : array) {
            if (Objects.equals(val, value)) {
                return true;
            }
        }
        return false;
    }
}



