package com.igrium.replaylab.object.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.anim.modifier.CurveModifierType;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.EditFlags;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObjectType;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.anim.Keyframe;
import com.igrium.replaylab.ui.widgets.KeyWidgets;
import com.igrium.replaylab.ui.widgets.PropertyWidgets;
import imgui.ImGui;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.slf4j.LoggerFactory;

public class DummyReplayObject extends ReplayObject {

    private enum TestEnum {VAL1, VAL2, VAL3}

    @Getter @Setter
    private double dummyValue;

    @Getter @Setter @NonNull
    private TestEnum testEnum = TestEnum.VAL1;

    public DummyReplayObject(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty("dummyValue", this::getDummyValue, this::setDummyValue);
        addProperty("testEnum", this::getTestEnumInt, this::setTestEnumInt);

        var ch = getOrCreateChannel("dummyValue");

        ch.getKeyframes().add(new Keyframe(0, 0));
        ch.getKeyframes().add(new Keyframe(4, 23));
    }

    private int getTestEnumInt() {
        return testEnum.ordinal();
    }

    private void setTestEnumInt(int ordinal) {
        setTestEnum(testEnumOrdinal(ordinal));
    }

    private static TestEnum testEnumOrdinal(int ordinal) {
        TestEnum[] vals = TestEnum.values();
        return vals[Math.clamp(ordinal, 0, vals.length - 1)];
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("dummyValue", getDummyValue());
        json.addProperty("testEnum", testEnum.name());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("dummyValue")) {
            setDummyValue(json.getAsJsonPrimitive("dummyValue").getAsDouble());
        }
        if (json.has("testEnum")) {
            setTestEnum(TestEnum.valueOf(json.getAsJsonPrimitive("testEnum").getAsString()));
        }
    }

    private final double[] dummyValInput = new double[1];

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = 0;
        dummyValInput[0] = getDummyValue();

        KeyWidgets.WidgetState state = PropertyWidgets.dragFloatN(this, "Dummy Value", 1, editor.getPlayhead(), "dummyValue");
        flags |= state.getEditFlags();

        if (ImGui.button("Add test modifier")) {
            var mod = CurveModifierType.TRANSLATE.create();
            mod.setOffsetY(10);
            var chan = getOrCreateChannel("dummyValue");
            chan.getModifiers().add(mod);

            LoggerFactory.getLogger("ReplayLab/DummyReplayObject").info("modifiers: {}", chan.getModifiers());
            flags |= EditFlags.CREATE_UNDO_STEP;
        }

        var state2 = PropertyWidgets.combo(this, "Test Enum", TestEnum.values().length, editor.getPlayhead(),
                i -> testEnumOrdinal(i).name(), "testEnum");

        flags |= state2.getEditFlags();

        return flags;
    }

    @Override
    public void apply(int timestamp) {

    }
}
