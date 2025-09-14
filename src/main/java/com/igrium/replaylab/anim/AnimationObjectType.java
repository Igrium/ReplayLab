package com.igrium.replaylab.anim;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.igrium.replaylab.scene.ReplayScene;

import java.util.function.Function;

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

    public String getId() {
        String id = REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This object type is not registered!");
        }
        return id;
    }

    public static final BiMap<String, AnimationObjectType<?>> REGISTRY = HashBiMap.create();


}
