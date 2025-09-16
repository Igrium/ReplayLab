package com.igrium.replaylab.scene.obj.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import imgui.ImGui;
import imgui.type.ImInt;
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

    public ScenePropsObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);
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
        json.addProperty("startTime", getStartTime());
        json.addProperty("length", getLength());
    }


    private final ImInt startTimeInput = new ImInt();
    private final ImInt lengthInput = new ImInt();

    @Override
    public boolean drawPropertiesPanel() {

        boolean modified = false;

        startTimeInput.set(startTime);
        if (ImGui.inputInt("Start Time", startTimeInput)) {
            modified = true;
        }
        startTime = startTimeInput.get();
        if (startTime < 0)
            startTime = 0;

        lengthInput.set(length);
        if (ImGui.inputInt("Length (ms)", lengthInput)) {
            modified = true;
        }
        length = lengthInput.get();
        if (length < 0)
            length = 0;

        return modified;
    }
}
