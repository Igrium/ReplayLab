package com.igrium.replaylab.scene.obj;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.scene.ScenePropsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplayObjectType<T extends ReplayObject> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplayObjectType.class);

    public ReplayObjectType(Factory<T> factory) {
        this.factory = factory;
    }

    public interface Factory<T extends ReplayObject> {
        T create(ReplayObjectType<T> type);
    }

    private final Factory<T> factory;

    public T create() {
        return factory.create(this);
    }

    public String getId() {
        String id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This object type is not registered!");
        }
        return id;
    }

    public static final BiMap<String, ReplayObjectType<?>> REGISTRY = HashBiMap.create();

    /**
     * Create a new replay object from a type ID.
     * @param typeId String ID of the type to create.
     * @return The new replay object.
     * @throws InvalidObjectTypeException If the specified type can't be found.
     */
    public static ReplayObject create(String typeId) throws InvalidObjectTypeException {
        var type = REGISTRY.get(typeId);
        if (type == null) {
            throw new InvalidObjectTypeException(typeId);
        }
        return type.create();
    }

    public static <T extends ReplayObject> ReplayObjectType<T> register(String id, Factory<T> factory) {
        var type = new ReplayObjectType<>(factory);
        if (REGISTRY.put(id, type) != null) {
            LOGGER.warn("Duplicate replay object type: {}", id);
        }
        return type;
    }

    public static final ReplayObjectType<ScenePropsObject> SCENE_PROPS = register("sceneProps", ScenePropsObject::new);
}
