package com.igrium.replaylab.render2;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.render2.capture.FrameCapture;
import com.igrium.replaylab.render2.capture.FrameCaptureType;
import com.igrium.replaylab.render2.encoder.EncoderConfig;
import com.igrium.replaylab.render2.encoder.EncoderType;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Language;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RenderSettingsObj extends ReplayObject {

    @Getter @NonNull
    private Path outPath = FabricLoader.getInstance().getGameDir().resolve("replay_videos/my_movie");

    public void setOutPath(@NonNull Path outPath) {
        this.outPath = outPath;
        pathStr.set(outPath.toString());
    }

    @Getter @Setter @NonNull
    private FrameCapture frameCapture = FrameCaptureType.BASIC.create();

    @Getter @Setter @NonNull
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

    private ImString pathStr = new ImString(64);


    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
