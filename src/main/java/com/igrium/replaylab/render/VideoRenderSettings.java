package com.igrium.replaylab.render;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.render.capture.FrameCaptureType;
import com.igrium.replaylab.render.capture.FrameCaptures;
import com.igrium.replaylab.render.writer.FrameWriterType;
import com.igrium.replaylab.render.writer.FrameWriters;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Tolerate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;

@JsonAdapter(VideoRenderSettingsSerializer.class)
public class VideoRenderSettings {
    private static final Logger LOGGER = ReplayLab.getLogger("VideoRenderSettings");

    @Getter
    private float fps = 24;

    @Getter
    private int width = 1920;

    @Getter
    private int height = 1080;

    @Getter @Setter @NonNull
    private Path outPath = FabricLoader.getInstance().getGameDir().resolve("replay_videos");

    @Getter @Setter @NonNull
    private FrameCaptureType<?, ?> frameCapture = FrameCaptures.OPENGL;

    @Getter @Setter @NonNull
    private FrameWriterType<?, ?> frameWriter = FrameWriters.PNG;

    @Getter @Setter @Nullable
    private Object frameCaptureConfig;

    @Getter @Setter @Nullable
    private Object frameWriterConfig;

    public void setFps(float fps) {
        this.fps = Math.max(.001f, fps);
    }

    public void setWidth(int width) {
        this.width = Math.max(1, width);
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
    }

    @Tolerate
    public void setFrameCapture(Identifier id) {
        var type = FrameCaptures.get(id);
        if (type == null) {
            LOGGER.warn("Unknown frame capture type: {}", id);
        } else {
            setFrameCapture(type);
        }
    }

    @Tolerate
    public void setFrameWriter(Identifier id) {
        var type = FrameWriters.get(id);
        if (type == null) {
            LOGGER.warn("Unknown frame writer type: {}", id);
        } else {
            setFrameWriter(type);
        }
    }
}

class VideoRenderSettingsSerializer implements JsonSerializer<VideoRenderSettings>, JsonDeserializer<VideoRenderSettings> {

    @Override
    public VideoRenderSettings deserialize(JsonElement elem, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject json = elem.getAsJsonObject();
        VideoRenderSettings settings = new VideoRenderSettings();

        if (json.has("fps")) {
            settings.setFps(json.get("fps").getAsFloat());
        }
        if (json.has("width")) {
            settings.setWidth(json.get("width").getAsInt());
        }
        if (json.has("height")) {
            settings.setHeight(json.get("height").getAsInt());
        }
        if (json.has("outPath")) {
            settings.setOutPath(Paths.get(json.get("outPath").getAsString()));
        }

        if (json.has("frameCapture")) {
            Identifier frameCapId = Identifier.of(json.get("frameCapture").getAsString());
            settings.setFrameCapture(frameCapId);
        }

        if (json.has("frameWriter")) {
            Identifier frameWriteId = Identifier.of(json.get("frameWriter").getAsString());
            settings.setFrameWriter(frameWriteId);
        }

        if (json.has("frameCaptureConfig")) {
            Object config = context.deserialize(json.get("frameCaptureConfig"), settings.getFrameCapture().getConfigClass());
            settings.setFrameCaptureConfig(config);
        }
        if (json.has("frameWriterConfig")) {
            Object config = context.deserialize(json.get("frameWriterConfig"), settings.getFrameWriter().getConfigClass());
            settings.setFrameWriterConfig(config);
        }

        return settings;
    }

    @Override
    public JsonElement serialize(VideoRenderSettings src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();

        json.addProperty("fps", src.getFps());
        json.addProperty("width", src.getWidth());
        json.addProperty("height", src.getHeight());
        json.addProperty("outPath", src.getOutPath().toString());
        json.addProperty("frameCapture", src.getFrameCapture().getId().toString());
        json.addProperty("frameWriter", src.getFrameWriter().getId().toString());

        if (src.getFrameCaptureConfig() != null) {
            json.add("frameCaptureConfig", context.serialize(src.getFrameCaptureConfig()));
        }

        if (src.getFrameWriterConfig() != null) {
            json.add("frameWriterConfig", context.serialize(src.getFrameWriterConfig()));
        }

        return json;
    }
}