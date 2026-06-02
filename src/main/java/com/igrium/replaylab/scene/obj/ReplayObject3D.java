package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.config.ReplayLabConfig;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.DynamicRotation;
import com.igrium.replaylab.math.DynamicRotation.RotationMode;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.scene.ReplayScene;
import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
import com.igrium.replaylab.ui.util.PropertyWidgets;
import com.igrium.replaylab.util.JsonUtils;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
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
    private final DynamicRotation rotation = new DynamicRotation();

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

    @Override
    public Transform3 getTransform(Transform3 dest) {
        return getBaseTransform(dest);
    }

    public final Transform3 getBaseTransform(Transform3 dest) {
        dest.identity();

        dest.pos().set(position);
        dest.rotate(rotation.getQuaternion(new Quaternionf()));
        dest.scale(scale);

        return dest;
    }

    public final void setBaseTransform(Transform3 transform) {
        transform.getPos(position);
        rotation.setQuaternion(transform.getRot(new Quaternionf()));
        transform.getScale(scale);
    }

    public final Matrix4f getBaseTransformMatrix(double centerX, double centerY, double centerZ, Matrix4f dest) {
        dest.identity();

        dest.setTranslation((float) (position.x - centerX), (float) (position.y - centerY), (float) (position.z - centerZ));
        dest.rotate(rotation.getQuaternion(new Quaternionf()));
        dest.scale(scale);

        return dest;
    }

    public final Matrix4f getBaseTransformMatrix(Vector3dc center, Matrix4f dest) {
        return getBaseTransformMatrix(center.x(), center.y(), center.z(), dest);
    }

    public final void setBaseTransformMatrix(double centerX, double centerY, double centerZ, Matrix4f matrix) {
        getMTranslation(matrix, position).add(centerX, centerY, centerZ);
        rotation.setQuaternion(matrix.getNormalizedRotation(new Quaternionf()));
        matrix.getScale(scale);
    }

    public final void setBaseTransformMatrix(Vector3dc center, Matrix4f matrix) {
        setBaseTransformMatrix(center.x(), center.y(), center.z(), matrix);
    }

    private boolean wasDragging;
    private final Matrix4f dragStartMatrix = new Matrix4f();

    @Override
    public PropertiesPanelState drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
        if (hideUI || !editor.isObjectActive(getId())) return PropertiesPanelState.NONE;

        Matrix4f modelMatrix = getBaseTransformMatrix(cameraPos, new Matrix4f());
        if (!wasDragging) {
            dragStartMatrix.set(modelMatrix);
        }
        int operation = 0;
        if (editor.showGizmoPos() && hasPos()) operation |= Operation.TRANSLATE;
        if (editor.showGizmoRot() && hasRot) operation |= Operation.ROTATE;
        if (editor.showGizmoScale() && hasScale()) operation |= Operation.SCALE;

        GizmoRenderer.manipulate(viewMatrix, projectionMatrix, operation,
                editor.isLocalGizmos() ? Mode.LOCAL : Mode.WORLD, modelMatrix);

        if (ImGuizmo.isUsing()) {
            wasDragging = true;
            setBaseTransformMatrix(cameraPos, modelMatrix);
            return PropertiesPanelState.DRAGGING;
        } else if (wasDragging) {
            boolean commit = !dragStartMatrix.equals(modelMatrix, .01f);
            wasDragging = false;
            return commit ? PropertiesPanelState.COMMIT : PropertiesPanelState.NONE;
        }
        return PropertiesPanelState.NONE;
    }

    private boolean startedDragging = false;

    @Override
    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {
        int pHead = editor.getPlayhead();
        boolean modified = false;

        modified |= hasPos && dragFloatN(t("gui.replaylab.pos"), .125f, pHead, 1, POS_X, POS_Y, POS_Z);

        if (getRotationMode() == RotationMode.QUATERNION) {
            modified |= hasRot && dragFloatN(t("gui.replaylab.rot"), .125f, pHead, 1, ROT_QUAT_W, ROT_QUAT_X, ROT_QUAT_Y, ROT_QUAT_Z);
        } else {
            float rotFactor = ReplayLabConfig.getInstance().isDisplayDegrees() ? MathHelper.DEGREES_PER_RADIAN : 1;
            modified |= hasRot && dragFloatN(t("gui.replaylab.rot"), 1, pHead, rotFactor, ROT_EULER_X, ROT_EULER_Y, ROT_EULER_Z);
        }

        modified |= hasScale && dragFloatN(t("gui.replaylab.scale"), 1, pHead, 1, SCALE_X, SCALE_Y, SCALE_Z);

        ImGui.separator();

        if (ImGui.beginCombo(t("gui.replaylab.rot_mode"), t(getRotationMode().getLabel()))) {
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
            position.set(0);
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
    private boolean dragFloatN(String name, float speed, int playhead, double factor, String... properties) {
        var state = PropertyWidgets.dragFloatN(this, name, speed, playhead, factor, properties);
        return state.isUpdated() || state.hasNewKey();
    }

    private static String t(String key) {
        return Language.getInstance().get(key) + "###" + key;
    }

    /**
     * Re-implementation of {@link Matrix4f#getTranslation} that uses Vector3d
     */
    private static Vector3d getMTranslation(Matrix4fc m, Vector3d dest) {
        dest.x = m.m30();
        dest.y = m.m31();
        dest.z = m.m32();
        return dest;
    }


}
