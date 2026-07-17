package com.igrium.replaylab.anim.constraints;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.anim.constraint.Constraint;
import com.igrium.replaylab.anim.constraint.ConstraintType;
import com.igrium.replaylab.anim.constraint.ObjectAccessor;
import com.igrium.replaylab.math.SimplexNoise;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import lombok.Getter;
import lombok.Setter;

public class Constraint3DNoise extends Constraint<ReplayObject3D> {

    private final double[] scale = new double[]{1};
    private final double[] intensity = new double[]{1};
    private final double[] phase = new double[1];
    private final double[] phaseDelta = new double[]{12f};

    @Getter @Setter
    private boolean usePos = true;

    @Getter @Setter
    private boolean useRot = true;

    public double getScale() {
        return scale[0];
    }

    public void setScale(double scale) {
        this.scale[0] = scale;
    }

    public double getIntensity() {
        return intensity[0];
    }

    public void setIntensity(double intensity) {
        this.intensity[0] = intensity;
    }

    public double getPhase() {
        return phase[0];
    }

    public void setPhase(double phase) {
        this.phase[0] = phase;
    }

    public double getPhaseDelta() {
        return phaseDelta[0];
    }

    public void setPhaseDelta(double phaseDelta) {
        this.phaseDelta[0] = phaseDelta;
    }

    public Constraint3DNoise(ConstraintType<ReplayObject3D, ?> type, ReplayObject3D object) {
        super(type, object);

        addProperty("scale", this::getScale, this::setScale);
        addProperty("intensity", this::getIntensity, this::setIntensity);
        addProperty("phase", this::getPhase, this::setPhase);
        addProperty("phaseDelta", this::getPhaseDelta, this::setPhaseDelta);
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        json.addProperty("scale", getScale());
        json.addProperty("intensity", getIntensity());
        json.addProperty("phase", getPhase());
        json.addProperty("phaseDelta", getPhaseDelta());
        json.addProperty("usePos", isUsePos());
        json.addProperty("useRot", isUseRot());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("scale")) {
            setScale(json.get("scale").getAsDouble());
        }
        if (json.has("intensity")) {
            setIntensity(json.get("intensity").getAsDouble());
        }

        if (json.has("phase")) {
            setPhase(json.get("phase").getAsDouble());
        }
        if (json.has("phaseDelta")) {
            setPhaseDelta(json.get("phaseDelta").getAsDouble());
        }
        if (json.has("usePos")) {
            setUsePos(json.get("usePos").getAsBoolean());
        }
        if (json.has("useRot")) {
            setUseRot(json.get("useRot").getAsBoolean());
        }
    }

    @Override
    public void evaluate(int time, ObjectAccessor objAccessor) {
        Transform3 transform = getObject().computedTransform();
        double i = getIntensity();
        double p = getPhase();
        double pd = getPhaseDelta();
        if (usePos) {
            double dx = applyNoise(time, getScale(), p) * i;
            double dy = applyNoise(time, getScale(), p + pd) * i;
            double dz = applyNoise(time, getScale(), p + pd * 2) * i;
            transform.translate(dx, dy, dz);
        }
        if (useRot) {
            // TODO: finish implementing
        }
    }

    private static double applyNoise(double xIn, double scale, double phase) {
        return SimplexNoise.noise((xIn / 1000) * scale, phase);
    }
}
