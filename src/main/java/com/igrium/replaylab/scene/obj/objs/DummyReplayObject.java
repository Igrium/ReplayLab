package com.igrium.replaylab.scene.obj.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import lombok.Getter;
import lombok.Setter;

public class DummyReplayObject extends ReplayObject {
    public DummyReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty("dummyValue", this::getDummyValue, this::setDummyValue);

        var ch = new KeyChannel();
        getChannels().put("dummyValue", ch);

        ch.getKeys().add(new Keyframe(0, 0));
        ch.getKeys().add(new Keyframe(4, 23));
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {

    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {

    }

    @Getter @Setter
    private double dummyValue;

    @Override
    public void apply(int timestamp) {

    }
}
