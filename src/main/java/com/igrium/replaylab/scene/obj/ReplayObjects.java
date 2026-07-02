package com.igrium.replaylab.scene.obj;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.render.RenderSettingsObj;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.objs.BlockDisplayObject;
import com.igrium.replaylab.scene.objs.CameraObject;
import com.igrium.replaylab.scene.objs.DummyReplayObject;
import com.igrium.replaylab.scene.objs.ScenePropsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class ReplayObjects {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayObjects");

    public static final BiMap<String, ReplayObjectType<?>> REGISTRY = HashBiMap.create();

    public static final ReplayObjectType<ScenePropsObject> SCENE_PROPS = register("sceneProps",
            new ReplayObjectType.Builder<>(ScenePropsObject::new)
                    .hideInOutliner()
                    .noManualSpawn()
    );

    public static final ReplayObjectType<RenderSettingsObj> RENDER_SETTINGS = register("renderSettings",
            new ReplayObjectType.Builder<>(RenderSettingsObj::new)
                    .noManualSpawn()
                    .hideInOutliner()
                    .hideInDopeSheet()
    );

    public static final ReplayObjectType<DummyReplayObject> DUMMY = register("dummy", DummyReplayObject::new);
    public static final ReplayObjectType<BlockDisplayObject> BLOCK_DISPLAY = register("blockDisplay",
            BlockDisplayObject::new);
    public static final ReplayObjectType<CameraObject> CAMERA = register("camera", CameraObject::new);

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
