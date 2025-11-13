package com.igrium.replaylab.render.writer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

@UtilityClass
public class FrameWriters {
    public static final BiMap<Identifier, FrameWriterType<?, ?>> REGISTRY = HashBiMap.create();

    public static final FrameWriterType<PNGFrameWriter, Object> PNG = registerSimple("replaylab:png", PNGFrameWriter::new);


    public static <T extends FrameWriterType<?, ?>> T register(String id, T type) {
        REGISTRY.put(Identifier.of(id), type);
        return type;
    }

    private static <T extends FrameWriter> FrameWriterType<T, Object> registerSimple(String id, FrameWriterType.Factory<T> factory) {
        var type = new FrameWriterType.Simple<>(factory);
        return register(id, type);
    }

    public static @Nullable FrameWriterType<?, ?> get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static @NotNull FrameWriterType<?, ?> getOrThrow(Identifier id) {
        var type = get(id);
        if (type == null) {
            throw new NullPointerException("Unknown frame writer type: " + id);
        }
        return type;
    }
}
