package com.igrium.replaylab.scene.obj.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import imgui.ImGui;
import imgui.flag.ImGuiDataType;
import imgui.type.ImDouble;
import lombok.Getter;
import lombok.Setter;

public class DummyReplayObject extends ReplayObject {

    @Getter @Setter
    private double dummyValue;

    public DummyReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty("dummyValue", this::getDummyValue, this::setDummyValue);

        var ch = getOrCreateChannel("dummyValue");

        ch.getKeyframes().add(new Keyframe(0, 0));
        ch.getKeyframes().add(new Keyframe(4, 23));
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("dummyValue", getDummyValue());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("dummyValue")) {
            setDummyValue(json.getAsJsonPrimitive("dummyValue").getAsDouble());
        }
    }

    private final ImDouble dummyValInput = new ImDouble();

    @Override
    public PropertiesPanelState drawPropertiesPanel() {
        boolean modified = false;

        dummyValInput.set(dummyValue);
        if (ImGui.dragScalar("Dummy Value", ImGuiDataType.Double, dummyValInput,1)) {
            modified = true;
        }
//        if (ImGui.inputDouble("Dummy Value", dummyValInput)) {
//            modified = true;
//        }
        dummyValue = dummyValInput.get();

        return modified ? PropertiesPanelState.DRAGGING : PropertiesPanelState.NONE;
    }

    @Override
    public boolean insertKey(int timestamp) {
        super.insertKey(timestamp);
        getOrCreateChannel("dummyValue").addKeyframe(timestamp, dummyValue);
        return true;
    }

    @Override
    public void apply(int timestamp) {

    }
}
