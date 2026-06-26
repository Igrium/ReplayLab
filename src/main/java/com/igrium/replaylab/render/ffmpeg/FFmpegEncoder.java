package com.igrium.replaylab.render.ffmpeg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderType;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class FFmpegEncoder extends EncoderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/FFmpegEncoder");

    public enum CodecFamily {
        STANDARD, VPX, PRO
    }

    public record CodecType(String encoderName, boolean supportsBitrate, CodecFamily family, String profile) {
        public CodecType(String encoderName, boolean supportsBitrate, CodecFamily family) {
            this(encoderName, supportsBitrate, family, "");
        }
    }


    public enum RateControlMode {
        CBR, VBR, CRF;

        public String langKey() {
            return "gui.ratecontrol." + name().toLowerCase();
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
            return "gui.bitrate." + name().toLowerCase();
        }
    }

    public enum EncodingPreset {
        ULTRAFAST, SUPERFAST, VERYFAST, FASTER, FAST, MEDIUM, SLOW, SLOWER, VERYSLOW;

        public String langKey() {
            return "gui.encpreset." + name().toLowerCase();
        }
    }

//    public record BitratePreset(String label, int bitrate) {};

    public static final Map<String, List<String>> CONTAINERS = ImmutableMap.of(
            "mp4", List.of("h.264", "h.265", "av1"),
            "mkv", List.of("h.264", "h.265", "av1", "vp8", "vp9", "prores", "dnxhr"),
            "mov", List.of("h.264", "h.265", "prores", "dnhxr"),
            "webm", List.of("vp8", "vp9", "av1")
    );

    // TODO: multiple prores versions
    public static final Map<String, CodecType> CODECS = ImmutableMap.of(
            "h.264", new CodecType("libx264", true, CodecFamily.STANDARD),
            "h.265", new CodecType("libx265", true, CodecFamily.STANDARD),
            "av1", new CodecType("libsvtav1", true, CodecFamily.STANDARD),
            "vp8", new CodecType("libvpx", true, CodecFamily.VPX),
            "vp9", new CodecType("libvpx-vp9", true, CodecFamily.VPX),
            "prores", new CodecType("prores_ks", false, CodecFamily.PRO, "1"), // ProRes 422 LT
            "dnxhr", new CodecType("dnxhd", false, CodecFamily.PRO, "dnxhr_hq")
    );


    @Getter
    @NonNull
    private String container = "mp4";

    public void setContainer(@NonNull String container) {
        this.container = container;
        List<String> codecs = CONTAINERS.get(this.container);
        if (codecs != null && !codecs.isEmpty() && !codecs.contains(this.codec)) {
            setCodec(codecs.getFirst());
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
    @Setter
    @NonNull
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

    @Override
    public void drawProperties(EditorState editor) {
        if (ImGui.beginCombo(t("gui.ffmpeg.container"), t("container." + container))) {
            for (var c : CONTAINERS.keySet()) {
                boolean selected = c.equals(this.container);
                if (drawComboItem(t("container." + c), selected)) {
                    setContainer(c);
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.beginCombo(t("gui.ffmpeg.codec"), t("codec." + codec))) {
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

        if (ImGui.beginCombo(t("gui.ffmpeg.rate_control"), t(rcMode.langKey()))) {
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
            if (ImGui.dragScalar(t("gui.ffmpeg.crf"), crfIn, .25f, 0, 63)) {
                setCrfValue(crfIn[0]);
            }
            ImGui.setItemTooltip(tt("gui.ffmpeg.crf.tooltip"));

        } else {
            String label = bitratePreset != null ? bitratePreset.langKey() : "gui.bitrate.custom";
            if (ImGui.beginCombo(t("gui.ffmpeg.quality"), tt(label))) {
                for (var preset : BitratePreset.values()) {
                    if (drawComboItem(t(preset.langKey()), preset == this.bitratePreset)) {
                        setBitratePreset(preset);
                    }
                }
                if (drawComboItem(t("gui.bitrate.custom"), this.bitratePreset == null)) {
                    setBitratePreset(null);
                }

                ImGui.endCombo();
            }

            if (bitratePreset == null) {
                ImInt brInput = new ImInt(bitrate);
                if (ImGui.inputInt(t("gui.ffmpeg.bitrate_val"), brInput, 1000, 5000)) {
                    setBitrate(brInput.get());
                }
            }
        }

        ImGui.separator();

        if (ImGui.beginCombo(t("gui.ffmpeg.preset"), t(encPreset.langKey()))) {
            for (var preset : EncodingPreset.values()) {
                if (drawComboItem(t(preset.langKey()), preset == this.encPreset)) {
                    setEncPreset(preset);
                }
            }
            ImGui.endCombo();
        }

        ImGui.setItemTooltip(tt("gui.ffmpeg.preset.tooltip"));
    }

    private static boolean drawComboItem(String label, boolean selected) {
        boolean success = ImGui.selectable(label, selected);
        if (selected) {
            ImGui.setItemDefaultFocus();
        }
        return success;
    }

    private boolean isCodecAllowed(String codec) {
        var allowed = CONTAINERS.get(container);
        return allowed == null || allowed.contains(codec);
    }


    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}



