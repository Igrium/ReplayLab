package com.igrium.replaylab.anim.modifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class CurveModifierType<T extends CurveModifier> {
    private final Function<CurveModifierType<T>, T> factory;

    public CurveModifierType(Function<CurveModifierType<T>, T> factory) {
        this.factory = factory;
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

    public static final BiMap<Identifier, CurveModifierType<?>> REGISTRY = HashBiMap.create();
}
