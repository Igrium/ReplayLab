package com.igrium.replaylab.render;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.capture.FrameCaptureType;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderType;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.util.RenderUtils;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RenderSettingsObj extends ReplayObject {

    @Getter
    @NonNull
    private Path outPath = FabricLoader.getInstance().getGameDir().resolve("replay_videos/my_movie");

    public void setOutPath(@NonNull Path outPath) {
        this.outPath = outPath;
        pathStr.set(outPath.toString());
    }

    @Getter
    @Setter
    @NonNull
    private FrameCapture frameCapture = FrameCaptureType.BASIC.create();

    @Getter
    @Setter
    @NonNull
    private EncoderConfig encoder = EncoderType.PNG.create();

    public RenderSettingsObj(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("outPath", outPath.toString());
        json.add("capture", FrameCaptureType.write(frameCapture, context));
        json.add("encoder", EncoderType.write(encoder, context));
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("outPath")) {
            setOutPath(Paths.get(json.get("outPath").getAsString()));
        }
        if (json.has("capture")) {
            setFrameCapture(FrameCaptureType.parse(json.getAsJsonObject("capture"), context));
        }
        if (json.has("encoder")) {
            setEncoder(EncoderType.parse(json.getAsJsonObject("encoder"), context));
        }
    }

    @Override
    public void apply(int timestamp) {

    }

    private final ImString pathStr = new ImString(256);

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        ImGui.text(tt("gui.replaylab.outputFile"));
        if (ImGui.button(t("gui.replaylab.browse"))) {
            FileDialogs.showSaveDialog(getOutPath().getParent().toString(), getOutPath().getFileName().toString()).thenAcceptAsync(opt -> {
                opt.ifPresent(s -> setOutPath(Paths.get(s)));
            }, RenderUtils::onRenderThread);
        }

        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);

        if (ImGui.inputText("##filepath", pathStr)) {
            outPath = Paths.get(pathStr.get());
        }

        ImGui.separator();
        Identifier selId = encoder.getType().getId();
        if (ImGui.beginCombo(t("gui.replaylab.encoder"), t(selId.toTranslationKey("encoder")))) {
            for (var entry : EncoderType.REGISTRY.entrySet()) {
                Identifier id = entry.getKey();
                boolean selected = id.equals(selId);

                if (ImGui.selectable(t(id.toTranslationKey("encoder")), selected) && !selected) {
                    setEncoder(entry.getValue().create());
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        ImGui.separator();
        getEncoder().drawProperties(editor);

        return 0;
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }
}
