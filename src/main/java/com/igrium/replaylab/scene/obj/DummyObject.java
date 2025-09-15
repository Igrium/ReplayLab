package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannelCategory;
import lombok.NonNull;

import java.util.List;

public class DummyObject extends AnimationObject {
    protected DummyObject(@NonNull AnimationObjectType<?> type, @NonNull ReplayScene scene) {
        super(type, scene);
    }

    @Override
    public void apply(KeyChannelCategory keyframes, int timestamp) {

    }

    @Override
    public List<String> listChannelNames() {
        return List.of("Channel 1", "Channel 2");
    }
}
