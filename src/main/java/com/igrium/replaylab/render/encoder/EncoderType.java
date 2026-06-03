package com.igrium.replaylab.render.encoder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@Builder
public final class EncoderType<T extends Encoder> {

    /// === REGISTRY ===

    public static final BiMap<Identifier, EncoderType<?>> REGISTRY = Maps.synchronizedBiMap(HashBiMap.create());

//    public static final EncoderType<Encoder> BASIC = register(EncoderType.builder()
//            .encoderClass(Encoder.class)
//            .factory(Encoder::new)
//            .parser(Encoder::readJson)
//            .serializer(Encoder::writeJson)
//            .build(), Identifier.of("replaylab:basic"));

    public static <T extends Encoder> EncoderType<T> register(EncoderType<T> type, Identifier id) {
        REGISTRY.put(id, type);
        return type;
    }

    /// === FIELDS ===

    @Getter @NonNull
    private final Class<T> encoderClass;

    @Getter @NonNull
    private final Function<EncoderType<T>, T> factory;

    @Getter @NonNull
    private final BiFunction<EncoderType<T>, JsonObject, T> parser;

    @Getter @NonNull
    private final BiConsumer<T, JsonObject> serializer;

    /// === CONSTRUCTOR ===

    public EncoderType(@NonNull Class<T> encoderClass, @NonNull Function<EncoderType<T>, T> factory,
                       @NonNull BiFunction<EncoderType<T>, JsonObject, T> parser,
                       @NonNull BiConsumer<T, JsonObject> serializer) {
        this.encoderClass = encoderClass;
        this.factory = factory;
        this.parser = parser;
        this.serializer = serializer;
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

    public static Encoder parse(JsonObject json) throws JsonParseException, UnknownEncoderTypeException {
        var id = Identifier.of(json.get("type").getAsString());
        var type = REGISTRY.get(id);
        if (type == null) {
            throw new UnknownEncoderTypeException(id);
        }
        return type.parseJson(json);
    }

    public T parseJson(JsonObject json) throws JsonParseException {
        return parser.apply(this, json);
    }

    public JsonObject save(Encoder instance, JsonObject dest) {
        T cast = encoderClass.cast(instance);
        serializer.accept(cast, dest);
        var id = tryGetId();
        dest.addProperty("type", id != null ? id.toString() : null);
        return dest;
    }

    /// === FACTORY ===

    public T create() {
        return factory.apply(this);
    }
}