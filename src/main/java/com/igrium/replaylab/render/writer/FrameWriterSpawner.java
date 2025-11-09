package com.igrium.replaylab.render.writer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.render.VideoRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class FrameWriterSpawner<T extends FrameWriter> {
    public abstract T create(VideoRenderer renderer);

    public void readConfig(JsonObject json, JsonDeserializationContext context) {
    }

    public void writeConfig(JsonObject json, JsonSerializationContext context) {
    }

    public void resetConfig() {
    }

    public boolean useFolderSelection() {
        return false;
    }

    public FileDialogs.FileFilter @Nullable [] getFileFilters() {
        return null;
    }

    public static <T extends FrameWriter> FrameWriterSpawner<T> create(Function<VideoRenderer, T> factory) {
        return new Simple<>(factory);
    }

    private static class Simple<T extends FrameWriter> extends FrameWriterSpawner<T> {

        private final Function<VideoRenderer, T> factory;

        private Simple(Function<VideoRenderer, T> factory) {
            this.factory = factory;
        }

        @Override
        public T create(VideoRenderer renderer) {
            return factory.apply(renderer);
        }
    }
}
