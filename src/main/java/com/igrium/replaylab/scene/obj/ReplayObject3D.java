package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.ui.util.PropertyWidgets;
import com.igrium.replaylab.util.JsonUtils;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.util.Language;
import org.joml.*;

/**
 * An object with a 3-dimensional transform
 */
public abstract class ReplayObject3D extends ReplayObject implements TransformProvider {

    public enum RotationMode {
        QUATERNION,
        EULER_YXZ
    }

    private static final String POS_X = "posX";
    private static final String POS_Y = "posY";
    private static final String POS_Z = "posZ";

    private static final String ROT_QUAT_W = "rotQuatW";
    private static final String ROT_QUAT_X = "rotQuatX";
    private static final String ROT_QUAT_Y = "rotQuatY";
    private static final String ROT_QUAT_Z = "rotQuatZ";

    private static final String ROT_EULER_X = "rotEulerX";
    private static final String ROT_EULER_Y = "rotEulerY";
    private static final String ROT_EULER_Z = "rotEulerZ";

    private static final String SCALE_X = "scaleX";
    private static final String SCALE_Y = "scaleY";
    private static final String SCALE_Z = "scaleZ";

    @Getter @Accessors(fluent = true)
    private boolean supportsPos = true;

    protected void setSupportsPos(boolean supportsPos) {
        this.supportsPos = supportsPos;
    }

    @Getter @Accessors(fluent = true)
    private boolean supportsRot = true;

    protected void setSupportsRot(boolean supportsRot) {
        this.supportsRot = supportsRot;
    }

    @Getter @Accessors(fluent = true)
    private boolean supportsScale = false;

    protected void setSupportsScale(boolean supportsScale) {
        this.supportsScale = supportsScale;
    }

    /**
     * The global position of this object (mutable)
     */
    @Getter
    private final Vector3d position = new Vector3d();

    /**
     * The quaternion rotation of this object (mutable)
     */
    @Getter
    private final Quaternionf rotationQuat = new Quaternionf();

    /**
     * The euler rotation of this object in radians (mutable)
     */
    @Getter
    private final Vector3f rotationEuler = new Vector3f();

    /**
     * The XYZ scale of this object (mutable)
     */
    @Getter
    private final Vector3f scale = new Vector3f(1);

    @Getter @Setter
    private RotationMode rotationMode = RotationMode.QUATERNION;

    public ReplayObject3D(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty(POS_X, position::x, v -> position.x = v);
        addProperty(POS_Y, position::y, v -> position.y = v);
        addProperty(POS_Z, position::z, v -> position.z = v);

        addProperty(ROT_QUAT_W, rotationQuat::w, v -> rotationQuat.w = (float) v);
        addProperty(ROT_QUAT_X, rotationQuat::x, v -> rotationQuat.x = (float) v);
        addProperty(ROT_QUAT_Y, rotationQuat::y, v -> rotationQuat.y = (float) v);
        addProperty(ROT_QUAT_Z, rotationQuat::z, v -> rotationQuat.z = (float) v);

        addProperty(ROT_EULER_X, rotationEuler::x, v -> rotationEuler.x = (float) v);
        addProperty(ROT_EULER_Y, rotationEuler::y, v -> rotationEuler.y = (float) v);
        addProperty(ROT_EULER_Z, rotationEuler::z, v -> rotationEuler.z = (float) v);

        addProperty(SCALE_X, scale::x, v -> scale.x = (float) v);
        addProperty(SCALE_Y, scale::y, v -> scale.y = (float) v);
        addProperty(SCALE_Z, scale::z, v -> scale.z = (float) v);
    }


    @Override
    public Transform3 getTransform(Transform3 dest) {
        return getBaseTransform(dest);
    }

    public final Transform3 getBaseTransform(Transform3 dest) {
        dest.identity();
        if (supportsPos) {
            dest.pos().set(position);
        }

        if (supportsRot) {
            switch (rotationMode) {
                case QUATERNION -> dest.rotate(rotationQuat);
                // Joml uses right-handed coordinates
                case EULER_YXZ -> dest.rotate(new Quaternionf().rotateYXZ(
                        -rotationEuler.y,
                        rotationEuler.x,
                        rotationEuler.z));
            }
        }

        if (supportsScale) {
            dest.scale(scale);
        }

        return dest;
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        if (supportsPos) {
            json.add("position", JsonUtils.writeJsonVec(position));
        }
        if (supportsRot) {
            json.add("rotation_quat", JsonUtils.writeJsonQuat(rotationQuat));
            json.add("rotation_euler", JsonUtils.writeJsonVec(rotationEuler));
        }
        if (supportsScale) {
            json.add("scale", JsonUtils.writeJsonVec(scale));
        }

        json.addProperty("rotationMode", rotationMode.name());
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("position")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("position"), position);
        } else {
            position.set(0);
        }

        if (json.has("rotation_quat")) {
            JsonUtils.readJsonQuat(json.getAsJsonArray("rotation_quat"), rotationQuat);
        } else {
            rotationQuat.identity();
        }

        if (json.has("rotation_euler")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("rotation_euler"), rotationEuler);
        } else {
            rotationEuler.set(0);
        }

        if (json.has("scale")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("scale"), scale);
        } else {
            scale.set(1);
        }

        if (json.has("rotationMode")) {
            setRotationMode(RotationMode.valueOf(json.getAsJsonPrimitive("rotationMode").getAsString()));
        }
    }

    boolean startedDragging = false;

    private final ImBoolean tmpBool = new ImBoolean();

    @Override
    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {
        int pHead = editor.getPlayhead();
        boolean modified = false;

        modified |= supportsPos && dragFloatN("Position", .125f, pHead, POS_X, POS_Y, POS_Z);
        if (rotationMode == RotationMode.QUATERNION) {
            modified |= supportsRot && dragFloatN("Rotation", 1, pHead, ROT_QUAT_W, ROT_QUAT_X, ROT_QUAT_Y, ROT_QUAT_Z);
        } else {
            modified |= supportsRot && dragFloatN("Rotation", 1, pHead, ROT_EULER_X, ROT_EULER_Y, ROT_EULER_Z);
        }

        modified |= supportsScale && dragFloatN("Scale", 1, pHead, SCALE_X, SCALE_Y, SCALE_Z);

        if (ImGui.beginCombo("Rotation Mode", rotModeLabel(rotationMode))) {
            for (RotationMode mode : RotationMode.values()) {
                tmpBool.set(mode == rotationMode);

                if (ImGui.selectable(rotModeLabel(mode), tmpBool)) {
                    modified = true;
                    setRotationMode(mode);
                }
            }
            ImGui.endCombo();
        }

        if (modified || (startedDragging && ImGui.isMouseDown(0))) {
            startedDragging = true;
            return PropertiesPanelState.DRAGGING;
        } else if (startedDragging) {
            startedDragging = false;
            return PropertiesPanelState.COMMIT;
        } else {
            return PropertiesPanelState.NONE;
        }


//        if (ImGui.beginCombo("Rotation Mode"))
    }

    // Wrapper to reduce code bloat
    private boolean dragFloatN(String name, float speed, int playhead, String... properties) {
        var state = PropertyWidgets.dragFloatN(this, name, speed, playhead, properties);
        return state.isUpdated() || state.hasNewKey();
    }

    private static String rotModeLabel(RotationMode mode) {
        return Language.getInstance().get("rot_mode." + mode.name().toLowerCase());
    }
}
