package com.igrium.replaylab.render;

import com.igrium.replaylab.render.writer.FrameWriters;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public class VideoRenderSettings {
    @Getter
    private float fps = 24;

    @Getter @Setter @NonNull
    private Path outPath = FabricLoader.getInstance().getGameDir().resolve("replay_videos");

    @Getter @Setter @NonNull
    private Identifier frameWriter = FrameWriters.PNG.getId();

    @Getter
    private Object frameWriterSettings;

    public void setFps(float fps) {
        if (fps < .001f) {
            fps = .001f;
        }
        this.fps = fps;
    }
}
