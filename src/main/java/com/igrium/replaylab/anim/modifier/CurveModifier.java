package com.igrium.replaylab.anim.modifier;

import com.google.gson.*;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.lang.reflect.Type;

public abstract class CurveModifier {
    @Getter
    private final CurveModifierType<?> type;

    @Getter @Setter
    private boolean restrictRange;

    @Getter @Setter
    private int start;

    @Getter @Setter
    private int end;

    @Getter @Setter
    private int blendIn;

    @Getter @Setter
    private int blendOut;

    public CurveModifier(CurveModifierType<?> type) {
        this.type = type;
    }

    /**
     * Compute and apply this modifier
     *
     * @param timestamp Timestamp to use
     * @param intensity The intensity of the modifier [0 - 1]
     * @param sampler   A function to sample the curve at any given time
     * @return The sampled value
     * @implNote <b>May be called on any thread!</b> Implementations must ensure thread-safety
     */
    public abstract double compute(int timestamp, float intensity, Double2DoubleFunction sampler);

    /**
     * Compute and apply this modifier
     *
     * @param timestamp Timestamp to use
     * @param sampler   A function to sample the curve at any given time
     * @return The sampled value
     * @implNote <b>May be called on any thread!</b> Implementations must ensure thread-safety
     */
    public final double compute(int timestamp, Double2DoubleFunction sampler) {
        float intensity;
        if (isRestrictRange()) {
            if (timestamp <= start || timestamp >= end) {
                intensity = 0f;
            } else {
                float inFactor = 1f;
                float outFactor = 1f;

                // Inward from the start point
                if (blendIn > 0 && timestamp < start + blendIn) {
                    inFactor = (float) (timestamp - start) / blendIn;
                }

                // Inward from the end point
                if (blendOut > 0 && timestamp > end - blendOut) {
                    outFactor = (float) (end - timestamp) / blendOut;
                }

                intensity = Math.min(inFactor, outFactor);
            }
        } else {
            intensity = 1f;
        }
        return compute(timestamp, intensity, sampler);
    }

    private final ImBoolean tmpBool = new ImBoolean(false);
    private final ImInt tmpInt = new ImInt(0);

    /**
     * Called during the render process to draw this modifier's editable properties
     * @param editor The current editor state
     * @return {@link ObjectEditState}
     */
    public int drawPropertiesPanel(EditorState editor) {
        int flags = 0;

        ImGui.separator();

        ImGui.alignTextToFramePadding();
        boolean open = ImGui.treeNodeEx("##", ImGuiTreeNodeFlags.AllowItemOverlap);
        ImGui.sameLine();

        tmpBool.set(isRestrictRange());
        if (ImGui.checkbox(t("gui.replaymod.restrict_range"), tmpBool)) {
            setRestrictRange(tmpBool.get());
            flags |= ObjectEditState.RESAMPLE | ObjectEditState.COMMIT;
        }

        if (open) {
            ImGui.beginDisabled(!isRestrictRange());

            tmpInt.set(start);
            flags |= rangeInput(t("gui.replaylab.mod_start"), tmpInt);
            start = tmpInt.get();

            tmpInt.set(end);
            flags |= rangeInput(t("gui.replaylab.mod_end"), tmpInt);
            end = tmpInt.get();

            tmpInt.set(blendIn);
            flags |= rangeInput(t("gui.replaylab.mod_in"), tmpInt);
            blendIn = tmpInt.get();

            tmpInt.set(blendOut);
            flags |= rangeInput(t("gui.replaylab.mod_out"), tmpInt);
            blendOut = tmpInt.get();

            ImGui.endDisabled();
            ImGui.treePop();
        }

        return flags;
    }

    private static int rangeInput(String label, ImInt value) {
        int flags = 0;
        if (ReplayLabControls.inputTimestamp(label, value)) {
            flags = ObjectEditState.UPDATE_SCENE | ObjectEditState.RESAMPLE;
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            flags |= ObjectEditState.COMMIT;
        }
        return flags;
    }

    public void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("restrictRange")) {
            setRestrictRange(json.get("restrictRange").getAsBoolean());
        }
        if (json.has("start")) {
            setStart(json.get("start").getAsInt());
        }
        if (json.has("end")) {
            setEnd(json.get("end").getAsInt());
        }
        if (json.has("blendIn")) {
            setBlendIn(json.get("blendIn").getAsInt());
        }
        if (json.has("blendOut")) {
            setBlendOut(json.get("blendOut").getAsInt());
        }
    }

    public void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("restrictRange", isRestrictRange());
        json.addProperty("start", start);
        json.addProperty("end", end);
        json.addProperty("blendIn", blendIn);
        json.addProperty("blendOut", blendOut);
    }

    public final JsonObject toJson(JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        writeJson(json, context);
        json.addProperty("type", getType().getId().toString());
        return json;
    }

    public final CurveModifier copy() {
        return getType().copy(this);
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}