package com.igrium.replaylab.render.writer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.render.VideoRenderer;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;

import java.util.function.Function;

@UtilityClass
public final class FrameWriters {

    public static final BiMap<Identifier, FrameWriterType<?, ?>> REGISTRY = HashBiMap.create();

    public static final FrameWriterType<PNGFrameWriter, Object> PNG = register("replaylab:png", PNGFrameWriter::new);

    public static <T extends FrameWriter, C> FrameWriterType<T, C> register(String id, FrameWriterType<T, C> type) {
        REGISTRY.put(Identifier.of(id), type);
        return type;
    }

    public static <T extends FrameWriter> FrameWriterType<T, Object> register(String id, Function<VideoRenderer, T> factory) {
        return register(id, new FrameWriterType.Simple<>(factory));
    }

    /**
     * Create a frame writer using its default settings.
     *
     * @param id       Frame writer ID.
     * @param renderer The video renderer to use.
     * @return The created writer.
     */
    public static FrameWriter create(Identifier id, VideoRenderer renderer) {
        var type = REGISTRY.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown frame writer: " + id);
        }

        return spawn(type, renderer);
    }

    // Aren't generics fun??
    private static <C, T extends FrameWriterType<?, C>> FrameWriter spawn(T type, VideoRenderer renderer) {
        C config = type.newConfig();
        return type.create(renderer, config);
    }
}
