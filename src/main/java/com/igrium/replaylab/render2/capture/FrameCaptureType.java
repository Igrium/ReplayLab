package com.igrium.replaylab.render2.capture;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class FrameCaptureType<T extends FrameCapture> {

    /// === REGISTRY ===

    public static final BiMap<Identifier, FrameCaptureType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

    public static <T extends FrameCapture> FrameCaptureType<T> register(FrameCaptureType<T> type, Identifier id) {
        REGISTRY.put(id, type);
        return type;
    }

    /// === FIELDS ===

    @Getter @NonNull
    private final Function<FrameCaptureType<T>, T> factory;

    /// === CONSTRUCTOR ===

    public FrameCaptureType(@NonNull Function<FrameCaptureType<T>, T> factory) {
        this.factory = factory;
    }

    /// === IDENTITY ===

    public Identifier getId() {
        var id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This FrameCaptureType is not registered!");
        }
        return id;
    }

    public @Nullable Identifier tryGetId() {
        return REGISTRY.inverse().get(this);
    }

    /// === SERIALIZATION ===

    public static FrameCapture parse(JsonObject json, JsonDeserializationContext ctx) throws JsonParseException, UnknownFrameCaptureTypeException {
        var id = Identifier.of(json.get("type").getAsString());
        var type = REGISTRY.get(id);
        if (type == null) {
            throw new UnknownFrameCaptureTypeException(id);
        }
        FrameCapture capture = type.create();
        capture.readJson(json, ctx);
        return capture;
    }

    /// === FACTORY ===

    public T create() {
        return factory.apply(this);
    }
}