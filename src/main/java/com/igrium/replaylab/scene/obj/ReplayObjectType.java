package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;
import net.minecraft.util.Identifier;


public class ReplayObjectType<T extends ReplayObject> {

    public interface Factory<T extends ReplayObject> {
        T create(ReplayObjectType<T> type, ReplayScene scene);
    }

    private final Factory<T> factory;
    @Getter
    private final boolean noManualSpawn;

    public ReplayObjectType(Factory<T> factory, boolean noManualSpawn) {
        this.factory = factory;
        this.noManualSpawn = noManualSpawn;
    }

    public ReplayObjectType(Factory<T> factory) {
        this(factory, false);
    }

    public T create(ReplayScene scene) {
        return factory.create(this, scene);
    }

    public String getId() {
        String id = ReplayObjects.REGISTRY.inverse().get(this);
        if (id == null) {
            throw new IllegalStateException("This object type is not registered!");
        }
        return id;
    }

    public String getTranslationKey() {
        String id = getId();
        return "replayobject." + id;
    }
}
