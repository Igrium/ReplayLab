package com.igrium.replaylab.scene.objs;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.anim.modifier.CurveModifierType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import com.igrium.replaylab.scene.obj.ReplayObjectType;
import com.igrium.replaylab.ui.widgets.KeyWidgets;
import com.igrium.replaylab.ui.widgets.PropertyWidgets;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.LoggerFactory;

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

    private final double[] dummyValInput = new double[1];

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = 0;
        dummyValInput[0] = getDummyValue();

        KeyWidgets.WidgetState state = PropertyWidgets.dragFloatN(this, "Dummy Value", 1, editor.getPlayhead(), "dummyValue");
        if (state.isUpdated()) flags |= ObjectEditState.UPDATE_SCENE;
        if (state.isDropped()) flags |= ObjectEditState.CREATE_UNDO_STEP;

//        if (ImGui.dragScalar("Dummy Value", dummyValInput)) {
//            flags |= ObjectEditState.UPDATE_SCENE;
//            setDummyValue(dummyValInput[0]);
//        }
//        if (ImGui.isItemDeactivatedAfterEdit()) {
//            flags |= ObjectEditState.COMMIT;
//        }

        if (ImGui.button("Add test modifier")) {
            var mod = CurveModifierType.TRANSLATE.create();
            mod.setOffsetY(10);
            var chan = getOrCreateChannel("dummyValue");
            chan.getModifiers().add(mod);

            LoggerFactory.getLogger("ReplayLab/DummyReplayObject").info("modifiers: {}", chan.getModifiers());
            flags |= ObjectEditState.CREATE_UNDO_STEP;
        }

//        boolean modified = false;
//
//        dummyValInput[0] = dummyValue;
//        if (ImGui.dragScalar("Dummy Value", dummyValInput, 1)) {
//            modified = true;
//        }
//        dummyValue = dummyValInput[0];
//
//

        return flags;
    }

    @Override
    public void apply(int timestamp) {

    }
}
