package com.igrium.replaylab.render2.encoder;

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
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class EncoderType<T extends Encoder> {

    /// === REGISTRY ===

    public static final BiMap<Identifier, EncoderType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

    public static <T extends Encoder> EncoderType<T> register(EncoderType<T> type, Identifier id) {
        REGISTRY.put(id, type);
        return type;
    }

    public static final EncoderType<PNGEncoder> PNG =
            register(new EncoderType<PNGEncoder>(PNGEncoder::new), Identifier.of("replaylab:png"));

    /// === FIELDS ===

    @Getter @NonNull
    private final Function<EncoderType<T>, T> factory;

    /// === CONSTRUCTOR ===

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

    public @Nullable Identifier tryGetId() {
        return REGISTRY.inverse().get(this);
    }

    /// === SERIALIZATION ===

    public static Encoder parse(JsonObject json, JsonDeserializationContext ctx) throws JsonParseException, UnknownEncoderTypeException {
        var id = Identifier.of(json.get("type").getAsString());
        var type = REGISTRY.get(id);
        if (type == null) {
            throw new UnknownEncoderTypeException(id);
        }
        Encoder encoder = type.create();
        encoder.readJson(json, ctx);
        return encoder;
    }


    /// === FACTORY ===

    public T create() {
        return factory.apply(this);
    }
}