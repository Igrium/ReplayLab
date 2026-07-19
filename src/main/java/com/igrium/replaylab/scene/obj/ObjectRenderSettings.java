package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.craftui.file.FileDialogs.FileFilter;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render.RenderUtils;
import com.igrium.replaylab.render.capture.FrameCapture;
import com.igrium.replaylab.render.capture.FrameCaptureType;
import com.igrium.replaylab.render.encoder.EncoderConfig;
import com.igrium.replaylab.render.encoder.EncoderType;
import com.igrium.replaylab.render.encoder.EncoderTypes;
import com.igrium.replaylab.scene.ReplayScene;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ObjectRenderSettings extends ReplayObject {

    @Getter
    @Setter
    @NonNull
    private Path outPath = FabricLoader.getInstance().getGameDir().resolve("replay_videos/my_movie");

    @Getter
    @Setter
    @NonNull
    private FrameCapture frameCapture = FrameCaptureType.BASIC.create();

    @Getter
    @Setter
    @NonNull
    private EncoderConfig encoder = EncoderTypes.PNG.create();

    public ObjectRenderSettings(ReplayObjectType<?> type, ReplayScene scene) {
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

            String[] extensions = encoder.getSupportedExtensions();
            FileFilter[] filters;
            if (extensions.length > 0) {
                filters = new FileFilter[] {new FileFilter(encoder.getFileFilterName(), extensions)};
            } else {
                filters = new FileFilter[0];
            }

            FileDialogs.showSaveDialog(
                    getOutPath().getParent().toString(),
                    getOutPath().getFileName().toString(),
                    filters
            ).thenAcceptAsync(
                    opt -> opt.ifPresent(s -> setOutPath(Paths.get(s))),
                    RenderUtils::onRenderThread
            );
        }

        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);

        pathStr.set(outPath.toString());
        if (ImGui.inputText("##filepath", pathStr)) {
            outPath = Paths.get(pathStr.get());
        }

        // Ensure extension is correct
        String[] exts = getEncoder().getSupportedExtensions();
        if (exts.length > 0) {
            String ext = FilenameUtils.getExtension(pathStr.get()); // Avoids reallocation by using pathStr
            if (!arrayContains(exts, ext)) {
                String baseName = FilenameUtils.removeExtension(outPath.getFileName().toString());
                setOutPath(outPath.getParent().resolve(baseName + "." + exts[0]));
            }
        }

        ImGui.separator();
        Identifier selId = encoder.getType().getId();
        if (ImGui.beginCombo(t("gui.replaylab.encoder"), t(selId.toTranslationKey("encoder")))) {
            for (var entry : EncoderTypes.REGISTRY.entrySet()) {
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

    private static <T> boolean arrayContains(T[] array, T value) {
        for (T val : array) {
            if (Objects.equals(val, value)) {
                return true;
            }
        }
        return false;
    }
}
