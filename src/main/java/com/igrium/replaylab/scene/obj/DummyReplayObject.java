package com.igrium.replaylab.scene.obj;

import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.obj.objs.ReplayObject;

public class DummyReplayObject extends ReplayObject {
    public DummyReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
        getChannels().add(new KeyChannel("Dummy"));
    }

    @Override
    public void apply(int timestamp) {

    }
}
