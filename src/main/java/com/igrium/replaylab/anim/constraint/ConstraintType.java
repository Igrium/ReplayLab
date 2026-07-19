package com.igrium.replaylab.anim.constraint;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObject3D;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public final class ConstraintType<R, T extends Constraint<R>> {
    private final BiFunction<ConstraintType<R, T>, R, T> factory;
    @Getter
    private final Class<R> objectClass;

    public ConstraintType(BiFunction<ConstraintType<R, T>, R, T> factory, Class<R> objectClass) {
        this.factory = factory;
        this.objectClass = objectClass;
    }

    public T create(R object) {
        return factory.apply(this, object);
    }

    public String getId() {
        String id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This constraint type is not registered!");
        }
        return id;
    }

    public @Nullable String tryGetId() {
        return REGISTRY.inverse().get(this);
    }

    public String translationKey() {
        return "constraint." + tryGetId();
    }

    public static final BiMap<String, ConstraintType<?, ?>> REGISTRY = HashBiMap.create();

    public static <T extends ConstraintType<?, ?>> T register(String id, T type) {
        REGISTRY.put(id, type);
        return type;
    }

    public static Constraint<?> fromJson(ReplayObject obj, JsonObject json, JsonDeserializationContext ctx) {
        String id = json.has("type") ? json.get("type").getAsString() : null;
        ConstraintType<?, ?> type = REGISTRY.get(id);

        if (type == null) {
            throw new IllegalArgumentException("Unknown constraint type: " + id);
        }

        var constraint = create(type, obj);
        constraint.parse(json, ctx);
        return constraint;
    }

    /**
     * Create a constraint from a non-typed replay object
     *
     * @param type Constraint type to spawn
     * @param obj  Replay object to use
     * @return The spawned constraint
     * @throws ClassCastException If the constraint isn't applicable to the supplied object.
     */
    public static @NotNull <R, T extends Constraint<R>> T create(ConstraintType<R, T> type, ReplayObject obj) throws ClassCastException {
        R cast = type.getObjectClass().cast(obj);
        return type.create(cast);
    }

    private static final ConstraintType<ReplayObject3D, ConstraintParent> PARENT =
            register("parent", new ConstraintType<>(ConstraintParent::new, ReplayObject3D.class));

    public static final ConstraintType<ReplayObject3D, ConstraintNoise3D> NOISE_3D =
            register("noise3d", new ConstraintType<>(ConstraintNoise3D::new, ReplayObject3D.class));

}
