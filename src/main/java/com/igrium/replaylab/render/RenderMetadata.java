package com.igrium.replaylab.render;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Various config values relating
 */
@Getter @Accessors(fluent = true)
@EqualsAndHashCode @Builder
public class RenderMetadata {
    @NonNull
    @Builder.Default
    private Path outPath = FabricLoader.getInstance()
            .getGameDir()
            .resolve("replay_videos")
            .resolve("export");

    @Builder.Default
    private int width = 1920;

    @Builder.Default
    private int height = 1080;

    private int totalFrames;
    // TODO: should this be broken into int fps and fpsBase?
    @Builder.Default
    private float fps = 30;
}
