package com.igrium.replaylab.render.capture;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import lombok.experimental.UtilityClass;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class FrameCaptures {
    public static final BiMap<Identifier, FrameCaptureType<?, ?>> REGISTRY = HashBiMap.create();

//    public static <T extends FrameCapture, C> FrameCaptureType<T, C> register(String id, FrameCaptureType<T, C> type) {
//        REGISTRY.put(Identifier.of(id), type);
//        return type;
//    }

    public static <T extends FrameCaptureType<?, ?>> T register(String id, T type) {
        REGISTRY.put(Identifier.of(id), type);
        return type;
    }

    public static <T extends FrameCapture> FrameCaptureType<T, Object> registerSimple(String id, FrameCaptureType.Factory<T> factory) {
        var type = new FrameCaptureType.Simple<T>(factory);
        return register(id, type);
    }

    public static @Nullable FrameCaptureType<?, ?> get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static @NotNull FrameCaptureType<?, ?> getOrThrow(Identifier id) {
        var type = get(id);
        if (type == null) {
            throw new NullPointerException("Unknown frame capture type: " + id);
        }
        return type;
    }
}
