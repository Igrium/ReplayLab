package com.igrium.replaylab.render.writer;

import com.igrium.replaylab.render.VideoRenderer;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public final class FrameWriters {
    public interface Factory<T extends FrameWriter> {
        T create(VideoRenderer renderer);
    }

    public static final Map<Identifier, Factory<?>> REGISTRY = new HashMap<>();

    public static FrameWriter create(Identifier id, VideoRenderer renderer) {
        var factory = REGISTRY.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown frame writer type: " + id);
        }
        return factory.create(renderer);
    }
}
