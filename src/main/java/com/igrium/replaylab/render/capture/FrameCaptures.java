package com.igrium.replaylab.render.capture;

import com.igrium.replaylab.render.VideoRenderer;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public final class FrameCaptures {
    public interface Factory<T extends FrameCapture> {
        T create(VideoRenderer renderer);
    }

    public static final Map<Identifier, Factory<?>> REGISTRY = new HashMap<>();

    {
        REGISTRY.put(Identifier.of("replaylab:opengl"), OpenGLFrameCapture::new);
    }

    public static FrameCapture create(Identifier id, VideoRenderer renderer) {
        var factory = REGISTRY.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown frame capture type: " + id);
        }
        return factory.create(renderer);
    }
}
