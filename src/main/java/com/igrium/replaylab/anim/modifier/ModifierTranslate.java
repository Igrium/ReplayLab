package com.igrium.replaylab.anim.modifier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.object.EditFlags;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.Getter;
import lombok.Setter;

import java.util.function.DoubleUnaryOperator;

public class ModifierTranslate extends CurveModifier {
    @Getter @Setter
    private int offsetX;

    @Getter @Setter
    private double offsetY;

    public ModifierTranslate(CurveModifierType<?> type) {
        super(type);
    }

    @Override
    public double compute(double timestamp, float intensity, DoubleUnaryOperator sampler) {
        return sampler.applyAsDouble(timestamp - offsetX * intensity) + offsetY * intensity;
    }

    private final ImInt intBuffer = new ImInt();
    private final double[] doubleBuffer = new double[1];

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = 0;
        intBuffer.set(offsetX);
        if (ReplayLabControls.inputTimestamp("Time Offset", intBuffer)) {
            offsetX = intBuffer.get();
            flags |= EditFlags.RESAMPLE | EditFlags.UPDATE_SCENE;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= EditFlags.COMMIT;
        }

        doubleBuffer[0] = offsetY;
        if (ImGui.dragScalar("Value Offset", doubleBuffer, .25f)) {
            offsetY = doubleBuffer[0];
            flags |= EditFlags.RESAMPLE |  EditFlags.UPDATE_SCENE;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= EditFlags.COMMIT;
        }

        return flags | super.drawPropertiesPanel(editor);
    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {
        super.readJson(json, context);
        if (json.has("offsetX")) {
            this.offsetX = json.get("offsetX").getAsInt();
        }

        if (json.has("offsetY")) {
            this.offsetY = json.get("offsetY").getAsDouble();
        }
    }

    @Override
    public void writeJson(JsonObject json, JsonSerializationContext context) {
        super.writeJson(json, context);
        json.addProperty("offsetX", this.offsetX);
        json.addProperty("offsetY", this.offsetY);
    }
}
