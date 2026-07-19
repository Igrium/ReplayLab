package com.igrium.replaylab.anim.constraint;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.SimplexNoise;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.obj.EditFlags;
import com.igrium.replaylab.scene.obj.ReplayObject3D;
import com.igrium.replaylab.ui.widgets.PropertyWidgets;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Language;
import org.apache.commons.lang3.function.BooleanConsumer;

public class ConstraintNoise3D extends Constraint<ReplayObject3D> {

    private static final String SPEED = "speed";
    private static final String INTENSITY = "intensity";
    private static final String PHASE = "phase";
    private static final String PHASE_DELTA = "phaseDelta";

    private final Config config = new Config();

    // Cut down on bloat so we can use gson
    @Getter @Setter
    private static final class Config {
        double speed = 1;
        double intensity = 1;
        double phase = 1;
        double phaseDelta = 1;

        boolean posX = true;
        boolean posY = true;
        boolean posZ = true;

        boolean rotX = true;
        boolean rotY = true;
        boolean rotZ = true;

        void copyFrom(Config config) {
            this.speed = config.speed;
            this.intensity = config.intensity;
            this.phase = config.phase;
            this.phaseDelta = config.phaseDelta;
            this.posX = config.posX;
            this.posY = config.posY;
            this.posZ = config.posZ;
            this.rotX = config.rotX;
            this.rotY = config.rotY;
        }
    }

    public ConstraintNoise3D(ConstraintType<ReplayObject3D, ?> type, ReplayObject3D object) {
        super(type, object);

        addProperty(SPEED, config::getSpeed, config::setSpeed);
        addProperty(INTENSITY, config::getIntensity, config::setIntensity);
        addProperty(PHASE, config::getPhase, config::setPhase);
        addProperty(PHASE_DELTA, config::getPhaseDelta, config::setPhaseDelta);
    }

    @Override
    protected JsonObject writeJson(JsonSerializationContext context) {
        return context.serialize(config).getAsJsonObject();
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        this.config.copyFrom(context.deserialize(json, Config.class));

    }

    @Override
    public void evaluate(int time, ObjectAccessor objAccessor) {
        Transform3 transform = getObject().computedTransform();
        double i = config.getIntensity();
        double p = config.getPhase();
        double pd = config.getPhaseDelta();

        double scale = config.getSpeed();

        {
            double dx = config.posX ? applyNoise(time, scale, p) * i : 0;
            double dy = config.posY ? applyNoise(time, scale, p + pd) * i : 0;
            double dz = config.posZ ? applyNoise(time, scale, pd * 2) * i : 0;
            transform.translate(dx, dy, dz);
        }

        {
            double dx = config.rotX ? applyNoise(time, scale, p * 3) * i * .5 : 0;
            double dy = config.rotY ? applyNoise(time, scale, pd * 4) * i * .5 : 0;
            double dz = config.rotZ ? applyNoise(time, scale, pd * 5) * i * .5 : 0;
            transform.rot().rotateEulerAuto((float) dx, (float) dy, (float) dz);
        }
    }

    private final ImBoolean tmpBool = new ImBoolean(false);

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = EditFlags.NONE;
        int ph = editor.getPlayhead();

        flags |= dragFloat(t("gui.replaylab.noise3d.speed"), ph, .1f, SPEED);
        flags |= dragFloat(t("gui.replaylab.noise3d.intensity"), ph, .05f, INTENSITY);
        flags |= dragFloat(t("gui.replaylab.noise3d.phase"), ph, .001f, PHASE);
        flags |= dragFloat(t("gui.replaylab.noise3d.phase_delta"), ph, .01f, PHASE_DELTA);
        ImGui.setItemTooltip(tt("gui.replaylab.noise3d.phase_delta.tooltip"));

        ImGui.spacing();
        ImGui.text(tt("gui.replaylab.affect"));

        flags |= check(t("gui.replaylab.posX"), config.posX, config::setPosX);
        flags |= check(t("gui.replaylab.posY"), config.posY, config::setPosY);
        flags |= check(t("gui.replaylab.posZ"), config.posZ, config::setPosZ);

        ImGui.spacing();

        flags |= check(t("gui.replaylab.rotX"), config.rotX, config::setRotX);
        flags |= check(t("gui.replaylab.rotY"), config.rotY, config::setRotY);
        flags |= check(t("gui.replaylab.rotZ"), config.rotZ, config::setRotZ);

        return flags;
    }

    // Avoid realloc
    private final String[] propCache = new String[1];

    private int dragFloat(String label, int ph, float speed, String prop) {
        propCache[0] = propName(prop);
        var state = PropertyWidgets.dragFloatN(getObject(), label, speed, ph, propCache);
        return state.getEditFlags();
    }

    private int check(String label, boolean value, BooleanConsumer setter) {
        tmpBool.set(value);
        if (ImGui.checkbox(label, tmpBool)) {
            setter.accept(tmpBool.get());
            return EditFlags.COMMIT;
        }
        return 0;
    }

    private static double applyNoise(double xIn, double scale, double phase) {
        return SimplexNoise.noise((xIn / 1000) * scale, phase);
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
