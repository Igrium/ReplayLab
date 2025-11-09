package com.igrium.replaylab.render.writer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.render.VideoRenderer;
import lombok.NonNull;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class FrameWriters {
    public static final BiMap<Identifier, FrameWriterSpawner<?>> REGISTRY = HashBiMap.create();

    public static final FrameWriterSpawner<PNGFrameWriter> PNG = register("replaylab:png", PNGFrameWriter::new);

    public static @NonNull FrameWriterSpawner<?> getOrThrow(Identifier id) {
        var spawner = REGISTRY.get(id);
        if (spawner == null) {
            throw new IllegalArgumentException("Unknown frame writer: " + id);
        }
        return spawner;
    }

    public static FrameWriter create(Identifier id, VideoRenderer renderer) {
        return getOrThrow(id).create(renderer);
    }

    public static <T extends FrameWriter> FrameWriterSpawner<T> register(String id, FrameWriterSpawner<T> spawner) {
        REGISTRY.put(Identifier.of(id), spawner);
        return spawner;
    }

    public static <T extends FrameWriter> FrameWriterSpawner<T> register(String id, Function<VideoRenderer, T> factory) {
        return register(id, FrameWriterSpawner.create(factory));
    }
}
