package com.igrium.replaylab.anim.modifier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.SimplexNoise;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.type.ImInt;

import net.minecraft.util.Language;

import java.util.function.DoubleUnaryOperator;

import static com.igrium.replaylab.scene.obj.ObjectEditState.*;

public class ModifierNoise extends CurveModifier {
    private final float[] scale = new float[]{1};
    private final float[] intensity = new float[]{1};
    private final ImInt offset = new ImInt(0);
    private final float[] phase = new float[1];

    public ModifierNoise(CurveModifierType<?> type) {
        super(type);
    }

    public float getScale() {
        return scale[0];
    }

    public void setScale(float scale) {
        this.scale[0] = scale;
    }

    public float getIntensity() {
        return intensity[0];
    }

    public void setIntensity(float intensity) {
        this.intensity[0] = intensity;
    }

    public int getOffset() {
        return offset.get();
    }

    public void setOffset(int offset) {
        this.offset.set(offset);
    }

    public float getPhase() {
        return phase[0];
    }

    public void setPhase(float phase) {
        this.phase[0] = phase;
    }

    @Override
    public double compute(double timestamp, float intensity, DoubleUnaryOperator sampler) {
        double base = sampler.applyAsDouble(timestamp);
        double i = getIntensity() * intensity;
        double noise = (SimplexNoise.noise((timestamp / 1000) * getScale() - getOffset(), getPhase())) * i;
        return base + noise;
    }

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = ObjectEditState.NONE;

        flags |= drawField(ImGui.dragFloat(t("gui.replaylab.noise.scale"), scale, .01f));
        flags |= drawField(ImGui.dragFloat(t("gui.replaylab.noise.intensity"), intensity, .01f));
        flags |= drawField(ReplayLabControls.inputTimestamp(t("gui.replaylab.noise.offset"), offset));
        flags |= drawField(ImGui.dragFloat(t("gui.replaylab.noise.phase"), phase, .001f));

        return flags | super.drawPropertiesPanel(editor);
    }

    private static int drawField(boolean editing) {
        int flags = editing ? RESAMPLE | UPDATE_SCENE : 0;
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= CREATE_UNDO_STEP;
        }
        return flags;
    }

    @Override
    public void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("scale", getScale());
        json.addProperty("intensity", getIntensity());
        json.addProperty("offset", getOffset());
        json.addProperty("phase", getPhase());
        super.writeJson(json, context);
    }

    @Override
    public void readJson(JsonObject json, JsonDeserializationContext context) {
        super.readJson(json, context);
        if (json.has("scale")) {
            setScale(json.get("scale").getAsFloat());
        }
        if (json.has("intensity")) {
            setIntensity(json.get("intensity").getAsFloat());
        }
        if (json.has("offset")) {
            setOffset(json.get("offset").getAsInt());
        }
        if (json.has("phase")) {
            setPhase(json.get("phase").getAsFloat());
        }
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
