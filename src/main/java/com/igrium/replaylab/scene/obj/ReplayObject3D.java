package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.RotationHolder;
import com.igrium.replaylab.math.RotationHolder.RotationMode;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.ui.util.PropertyWidgets;
import com.igrium.replaylab.util.JsonUtils;
import imgui.ImGui;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.util.Language;
import org.joml.*;

/**
 * An object with a 3-dimensional transform
 */
@Accessors(fluent = true)
public abstract class ReplayObject3D extends ReplayObject implements TransformProvider {

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

    @Getter
    private boolean hasPos = true;

    protected void setHasPos(boolean hasPos) {
        this.hasPos = hasPos;
    }

    @Getter
    private boolean hasRot = true;

    protected void setHasRot(boolean hasRot) {
        this.hasRot = hasRot;
    }

    @Getter
    private boolean hasScale = false;

    protected void setHasScale(boolean hasScale) {
        this.hasScale = hasScale;
    }

    /**
     * The global position of this object (mutable)
     */
    @Getter
    private final Vector3d position = new Vector3d();

    /**
     * The rotation of this object (mutable)
     */
    @Getter
    private final RotationHolder rotation = new RotationHolder();

    /**
     * The XYZ scale of this object (mutable)
     */
    @Getter
    private final Vector3f scale = new Vector3f(1);

    public ReplayObject3D(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        // Will get overwritten during deserialization
        setRotationMode(ReplayLabConfig.getInstance().getDefaultRotMode());

        addProperty(POS_X, position::x, v -> position.x = v);
        addProperty(POS_Y, position::y, v -> position.y = v);
        addProperty(POS_Z, position::z, v -> position.z = v);

        addProperty(ROT_QUAT_W, rotation.quaternion()::w, v -> rotation.quaternion().w = (float) v);
        addProperty(ROT_QUAT_X, rotation.quaternion()::x, v -> rotation.quaternion().x = (float) v);
        addProperty(ROT_QUAT_Y, rotation.quaternion()::y, v -> rotation.quaternion().y = (float) v);
        addProperty(ROT_QUAT_Z, rotation.quaternion()::z, v -> rotation.quaternion().z = (float) v);

        addProperty(ROT_EULER_X, rotation.euler()::x, v -> rotation.euler().x = (float) v);
        addProperty(ROT_EULER_Y, rotation.euler()::y, v -> rotation.euler().y = (float) v);
        addProperty(ROT_EULER_Z, rotation.euler()::z, v -> rotation.euler().z = (float) v);

        addProperty(SCALE_X, scale::x, v -> scale.x = (float) v);
        addProperty(SCALE_Y, scale::y, v -> scale.y = (float) v);
        addProperty(SCALE_Z, scale::z, v -> scale.z = (float) v);

    }

    public RotationMode getRotationMode() {
        return rotation.mode();
    }

    public void setRotationMode(RotationMode mode) {
        rotation.setMode(mode);
    }

    public final Transform3 getBaseTransform3(Transform3 dest) {
        dest.identity();
        if (hasPos()) {
            dest.pos().set(position);
        }

        if (hasRot()) {
            dest.rotate(rotation.getQuaternion(new Quaternionf()));
        }

        if (hasScale()) {
            dest.scale(scale);
        }

        return dest;
    }

    @Override
    public Transform3 getTransform(Transform3 dest) {
        return getBaseTransform3(dest);
    }

    boolean startedDragging = false;
    private final ImBoolean tmpBool = new ImBoolean();

    @Override
    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {
        int pHead = editor.getPlayhead();
        boolean modified = false;

        modified |= hasPos && dragFloatN("Position", .125f, pHead, POS_X, POS_Y, POS_Z);

        if (getRotationMode() == RotationMode.QUATERNION) {
            modified |= hasRot && dragFloatN("Rotation", 1, pHead, ROT_QUAT_W, ROT_QUAT_X, ROT_QUAT_Y, ROT_QUAT_Z);
        } else {
            modified |= hasRot && dragFloatN("Rotation", 1, pHead, ROT_EULER_X, ROT_EULER_Y, ROT_EULER_Z);
        }

        modified |= hasScale && dragFloatN("Scale", 1, pHead, SCALE_X, SCALE_Y, SCALE_Z);

        ImGui.separator();

        if (ImGui.beginCombo("Rotation Mode", t(getRotationMode().getLabel()))) {
            for (RotationMode mode : RotationMode.values()) {
                if (ImGui.selectable(t(mode.getLabel()), mode == getRotationMode())) {
                    rotation.setMode(mode, ReplayLabConfig.getInstance().isRotModeConvert());
                    modified = true;
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

    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        if (hasPos()) {
            json.add("position", JsonUtils.writeJsonVec(position));
        }
        if (hasRot()) {
            // TODO: Do we want to be storing the value of the unused rotation mode?
            json.add("rotationQuat", JsonUtils.writeJsonQuat(rotation.quaternion()));
            json.add("rotationEuler", JsonUtils.writeJsonVec(rotation.euler()));

            json.addProperty("rotationMode", rotation.mode().name());
        }
        if (hasScale()) {
            json.add("scale", JsonUtils.writeJsonVec(scale));
        }
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("position")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("position"), position);
        } else {
            position.set(9);
        }

        if (json.has("rotationQuat")) {
            JsonUtils.readJsonQuat(json.getAsJsonArray("rotationQuat"), rotation.quaternion());
        } else {
            rotation.quaternion().identity();
        }

        if (json.has("rotationEuler")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("rotationEuler"), rotation.euler());
        } else {
            rotation.euler().set(0);
        }

        if (json.has("rotationMode")) {
            String mode = json.getAsJsonPrimitive("rotationMode").getAsString();
            rotation.setMode(RotationMode.valueOf(mode), false);
        } else {
            rotation.setMode(ReplayLabConfig.getInstance().getDefaultRotMode(), false);
        }

        if (json.has("scale")) {
            JsonUtils.readJsonVec(json.getAsJsonArray("scale"), scale);
        }
    }

    // Wrapper to reduce code bloat
    private boolean dragFloatN(String name, float speed, int playhead, String... properties) {
        var state = PropertyWidgets.dragFloatN(this, name, speed, playhead, properties);
        return state.isUpdated() || state.hasNewKey();
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }
}
