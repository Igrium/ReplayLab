package com.igrium.replaylab.scene;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import lombok.Getter;

/**
 * Global scene properties. Exactly one of these should exist per-scene.
 */
public final class ScenePropsObject extends ReplayObject {

    @Getter
    private int startTime;

    public void setStartTime(int startTime) {
        if (startTime < 0) {
            throw new IllegalArgumentException("startTime may not be negative.");
        }
        this.startTime = startTime;
    }

    @Getter
    private int length = 10000; // 10 seconds default

    public void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length may not be negative.");
        }
        this.length = length;
    }

    public ScenePropsObject(ReplayObjectType<?> type) {
        super(type);
    }

    @Override
    public void apply(int timestamp) {
    }

    @Override
    protected void readProperties(JsonObject json, JsonDeserializationContext context) {
        super.readProperties(json, context);
        if (json.has("startTime")) {
            setStartTime(json.getAsJsonPrimitive("startTime").getAsInt());
        }
        if (json.has("length")) {
            setLength(json.getAsJsonPrimitive("length").getAsInt());
        }
    }

    @Override
    protected void writeProperties(JsonObject json, JsonSerializationContext context) {
        super.writeProperties(json, context);
        json.addProperty("startTime", getLength());
        json.addProperty("length", getLength());
    }
}
