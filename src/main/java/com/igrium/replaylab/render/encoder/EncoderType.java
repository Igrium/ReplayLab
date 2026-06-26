package com.igrium.replaylab.render.encoder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class EncoderType<T extends EncoderConfig> {

    /// === REGISTRY ===

    public static final BiMap<Identifier, EncoderType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

    public static <T extends EncoderConfig> EncoderType<T> register(EncoderType<T> type, Identifier id) {
        REGISTRY.put(id, type);
        return type;
    }

    public static final EncoderType<PNGEncoder> PNG = register(new EncoderType<>(PNGEncoder::new),
            Identifier.of("replaylab:png"));

    public static final EncoderType<DummyEncoder> DUMMY = register(new EncoderType<>(DummyEncoder::new),
            Identifier.of("replaylab:dummy"));

    /// === FIELDS ===

    @Getter
    @NonNull
    private final Function<EncoderType<T>, T> factory;

    public EncoderType(@NonNull Function<EncoderType<T>, T> factory) {
        this.factory = factory;
    }

    /// === IDENTITY ===

    public Identifier getId() {
        var id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This EncoderType is not registered!");
        }
        return id;
    }

    /// === FACTORY ===

    public T create() {
        return factory.apply(this);
    }

    /// === SERIALIZATION ===
    public static EncoderConfig parse(JsonObject json, JsonDeserializationContext ctx) throws JsonParseException,
            UnknownEncoderTypeException {
        var id = Identifier.of(json.get("type").getAsString());
        var type = REGISTRY.get(id);
        if (type == null) {
            throw new UnknownEncoderTypeException(id);
        }
        var encoder = type.create();
        encoder.readJson(json, ctx);
        return encoder;
    }

    public static JsonObject write(EncoderConfig encoder, JsonSerializationContext ctx) {
        var json = encoder.writeJson(ctx);
        json.addProperty("type", encoder.getType().getId().toString());
        return json;
    }
}
