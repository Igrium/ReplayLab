package com.igrium.replaylab.anim.modifier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
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
    public double compute(int timestamp, float intensity, Double2DoubleFunction sampler) {
        return sampler.get(timestamp - offsetX * intensity) + offsetY * intensity;
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
