package com.igrium.replaylab.anim.modifier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import imgui.ImGui;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import lombok.Getter;
import lombok.Setter;

public class ModifierTranslate extends CurveModifier {
    @Getter @Setter
    private double offsetX;

    @Getter @Setter
    private double offsetY;

    public ModifierTranslate(CurveModifierType<?> type) {
        super(type);
    }

    @Override
    public double compute(double timestamp, float intensity, Double2DoubleFunction sampler) {
        return sampler.get(timestamp - offsetX * intensity) + offsetY * intensity;
    }

    private final double[] doubleBuffer = new double[1];

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = 0;
        doubleBuffer[0] = offsetX;
        if (ImGui.dragScalar("Time Offset", doubleBuffer)) {
            offsetX = doubleBuffer[0];
            flags |= ObjectEditState.RESAMPLE | ObjectEditState.UPDATE_SCENE;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= ObjectEditState.COMMIT;
        }

        doubleBuffer[0] = offsetY;
        if (ImGui.dragScalar("Value Offset", doubleBuffer)) {
            offsetY = doubleBuffer[0];
            flags |= ObjectEditState.RESAMPLE |  ObjectEditState.UPDATE_SCENE;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= ObjectEditState.COMMIT;
        }

        return flags | super.drawPropertiesPanel(editor);
    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {
        super.readJson(json, context);
        if (json.has("offsetX")) {
            this.offsetX = json.get("offsetX").getAsDouble();
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
