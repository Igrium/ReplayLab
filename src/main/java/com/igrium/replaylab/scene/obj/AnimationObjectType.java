package com.igrium.replaylab.scene.obj;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.igrium.replaylab.scene.ReplayScene;

public class AnimationObjectType<T extends AnimationObject> {


    public interface Factory<T extends AnimationObject> {
        T create(AnimationObjectType<T> type, ReplayScene scene);
    }
    private final Factory<T> factory;

    public AnimationObjectType(Factory<T> factory) {
        this.factory = factory;
    }

    public T create(ReplayScene scene) {
        return factory.create(this, scene);
    }

    public String getId() throws IllegalStateException {
        String id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This object type is not registered!");
        }
        return id;
    }

    public static final BiMap<String, AnimationObjectType<?>> REGISTRY = HashBiMap.create();

    public static final AnimationObjectType<DummyObject> DUMMY = new AnimationObjectType<>(DummyObject::new);

    /**
     * Create an animation object from a type identifier.
     * @param type Type identifier string.
     * @param scene Scene to give the new instance.
     * @return Instantiated animation object.
     * @throws InvalidObjectTypeException If the supplied type does not exist.
     */
    public static AnimationObject create(String type, ReplayScene scene) throws InvalidObjectTypeException {
        AnimationObjectType<?> t = REGISTRY.get(type);
        if (t == null) {
            throw new InvalidObjectTypeException(type);
        }
        return t.create(scene);
    }

    /**
     * Parse an animation object from a json tree.
     *
     * @param json  Serialized form of animation object.
     * @param scene Scene to give the new object.
     * @return Instantiated object.
     * @throws IllegalArgumentException If <code>json</code> does not contain a valid <code>type</code> tag.
     */
    public static AnimationObject fromJson(JsonObject json, ReplayScene scene) throws InvalidObjectTypeException {
        JsonPrimitive typeE = json.getAsJsonPrimitive("type");
        if (typeE == null || !typeE.isString()) {
            throw new InvalidObjectTypeException(null);
        }

        AnimationObject obj = create(typeE.getAsString(), scene);
        obj.readJson(json);
        obj.setPrevSavedProperties(json);
        return obj;
    }

    /**
     * Serialize an animation object into a json tree, including its type identifier.
     *
     * @param obj  Object to save.
     * @param dest Json object to write into.
     * @return <code>dest</code>
     * @throws IllegalStateException If the object does not have a registered type.
     */
    public static JsonObject toJson(AnimationObject obj, JsonObject dest) throws IllegalStateException {
        obj.writeJson(dest);
        dest.addProperty("type", obj.getType().getId());
        return dest;
    }
}
