package com.igrium.replaylab.scene.obj;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.scene.ReplayScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class ReplayObjects {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayObjects");

    public static final BiMap<String, ReplayObjectType<?>> REGISTRY = HashBiMap.create();

    public static final ReplayObjectType<ObjectSceneProps> SCENE_PROPS = register("sceneProps",
            new ReplayObjectType.Builder<>(ObjectSceneProps::new)
                    .hideInOutliner()
                    .noManualSpawn()
    );

    public static final ReplayObjectType<ObjectRenderSettings> RENDER_SETTINGS = register("renderSettings",
            new ReplayObjectType.Builder<>(ObjectRenderSettings::new)
                    .noManualSpawn()
                    .hideInOutliner()
                    .hideInDopeSheet()
    );

    public static final ReplayObjectType<DummyReplayObject> DUMMY = register("dummy", DummyReplayObject::new);
    public static final ReplayObjectType<ObjectBlockDisplay> BLOCK_DISPLAY = register("blockDisplay",
            ObjectBlockDisplay::new);
    public static final ReplayObjectType<ObjectCamera> CAMERA = register("camera", ObjectCamera::new);
    public static final ReplayObjectType<ObjectEntityProxy> ENTITY_PROXY = register("entityProxy", ObjectEntityProxy::new);

    /**
     * Create a new replay object from a type ID.
     *
     * @param typeId String ID of the type to create.
     * @param scene  Scene to assign to the new object.
     * @return The new replay object.
     * @throws InvalidObjectTypeException If the specified type can't be found.
     */
    public static ReplayObject create(String typeId, ReplayScene scene) throws InvalidObjectTypeException {
        var type = REGISTRY.get(typeId);
        if (type == null) {
            throw new InvalidObjectTypeException(typeId);
        }
        return type.create(scene);
    }

    public static <T extends ReplayObject> ReplayObjectType<T> register(String id, ReplayObjectType.Factory<T> factory) {
        return register(id, new ReplayObjectType<>(factory));
    }

    public static <T extends ReplayObject> ReplayObjectType<T> register(String id, ReplayObjectType.Builder<T> builder) {
        return register(id, builder.build());
    }

    public static <T extends ReplayObject> ReplayObjectType<T> register(String id, ReplayObjectType<T> type) {
        if (REGISTRY.put(id, type) != null) {
            LOGGER.warn("Duplicate replay object type: {}", id);
        }
        return type;
    }

    /**
     * Instantiate a replay object from its serialized form.
     *
     * @param saved Serialized form of replay object.
     * @param scene Scene to assign to the new object.
     * @return New object.
     */
    public static ReplayObject deserialize(SerializedReplayObject saved, ReplayScene scene) {
        ReplayObject obj = create(saved.getType(), scene);
        obj.parse(saved);
        return obj;
    }

    public static Stream<ReplayObjectType<?>> getSpawnableTypes() {
        return REGISTRY.values().stream().filter(t -> !t.noManualSpawn());
    }
}
