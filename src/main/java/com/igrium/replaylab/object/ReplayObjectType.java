package com.igrium.replaylab.object;

import com.igrium.replaylab.scene.ReplayScene;
import lombok.Getter;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
public class ReplayObjectType<T extends ReplayObject> {

    public interface Factory<T extends ReplayObject> {
        T create(ReplayObjectType<T> type, ReplayScene scene);
    }

    private final Factory<T> factory;

    @Getter
    private final boolean noManualSpawn;

    @Getter
    private final boolean hideInOutliner;

    @Getter
    private final boolean hideInDopeSheet;

    public ReplayObjectType(Factory<T> factory, boolean noManualSpawn, boolean hideInOutliner, boolean hideInDopeSheet) {
        this.factory = factory;
        this.noManualSpawn = noManualSpawn;
        this.hideInOutliner = hideInOutliner;
        this.hideInDopeSheet = hideInDopeSheet;
    }

    public ReplayObjectType(Factory<T> factory) {
        this(factory, false, false, false);
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

    public static class Builder<T extends ReplayObject> {
        private final Factory<T> factory;
        private boolean noManualSpawn;
        private boolean hideInOutliner;
        private boolean hideInDopeSheet;

        public Builder(Factory<T> factory) {
            this.factory = factory;
        }

        public Builder<T> noManualSpawn() {
            this.noManualSpawn = true;
            return this;
        }

        public Builder<T> hideInOutliner() {
            this.hideInOutliner = true;
            return this;
        }

        public Builder<T> hideInDopeSheet() {
            this.hideInDopeSheet = true;
            return this;
        }

        public ReplayObjectType<T> build() {
            return new ReplayObjectType<>(factory, noManualSpawn, hideInOutliner, hideInDopeSheet);
        }
    }
}
