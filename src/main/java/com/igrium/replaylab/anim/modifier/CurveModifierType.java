package com.igrium.replaylab.anim.modifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.*;
import json.GsonSerializationContext;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class CurveModifierType<T extends CurveModifier> {
    private static final GsonSerializationContext jsonCtx = new GsonSerializationContext(new Gson());

    private final Function<CurveModifierType<T>, T> factory;

    public CurveModifierType(Function<CurveModifierType<T>, T> factory) {
        this.factory = factory;
    }

    public T copy(CurveModifier mod) {
        T newVal = create();
        newVal.readJson(mod.toJson(jsonCtx), jsonCtx);
        return newVal;
    }

    public T create() {
        return factory.apply(this);
    }

    public Identifier getId() {
        var id = REGISTRY.inverse().get(this);
        if (id == null) {
            // Should never happen, so blow up when it does
            throw new IllegalStateException("Curve modifier type has not been registered");
        }
        return id;
    }

    public String getTranslationKey() {
        return getId().toTranslationKey("curve_modifier");
    }

    public static CurveModifier fromJson(JsonObject json, JsonDeserializationContext context) {
        if (!json.has("type")) {
            throw new JsonParseException("Invalid json object. Missing 'type'");
        }
        Identifier id = Identifier.of(json.get("type").getAsString());
        CurveModifierType<?> type = CurveModifierType.REGISTRY.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown curve modifier type: " + id);
        }
        CurveModifier mod = type.create();
        mod.readJson(json, context);
        return mod;
    }

    public static final BiMap<Identifier, CurveModifierType<?>> REGISTRY = HashBiMap.create();

    public static <T extends CurveModifier> CurveModifierType<T> register(Identifier id, Function<CurveModifierType<T>, T> factory) {
        var type = new CurveModifierType<>(factory);
        REGISTRY.put(id, type);
        return type;
    }

    public static final CurveModifierType<ModifierTranslate> TRANSLATE =
            register(Identifier.of("replaylab:translate"), ModifierTranslate::new);
}
