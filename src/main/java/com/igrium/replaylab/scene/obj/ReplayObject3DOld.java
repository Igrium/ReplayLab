//package com.igrium.replaylab.scene.obj;
//
//import com.google.gson.JsonArray;
//import com.google.gson.JsonDeserializationContext;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonSerializationContext;
//import com.igrium.replaylab.editor.EditorState;
//import com.igrium.replaylab.math.MathUtils;
//import com.igrium.replaylab.math.Transform3;
//import com.igrium.replaylab.scene.ReplayScene;
//import com.igrium.replaylab.ui.gizmos.GizmoRenderer;
//import com.igrium.replaylab.ui.util.PropertyWidgets;
//import imgui.ImGui;
//import imgui.extension.imguizmo.ImGuizmo;
//import imgui.extension.imguizmo.flag.Mode;
//import imgui.extension.imguizmo.flag.Operation;
//import lombok.Getter;
//import net.minecraft.client.MinecraftClient;
//import net.minecraft.util.math.Vec3d;
//import org.joml.*;
//import org.joml.Math;
//
///**
// * An object with a 3-dimensional transform
// */
//public abstract class ReplayObject3DOld extends ReplayObject implements TransformProvider {
//
//    private static final String POS_X = "posX";
//    private static final String POS_Y = "posY";
//    private static final String POS_Z = "posZ";
//
//    private static final String ROT_W = "rotW";
//    private static final String ROT_X = "rotX";
//    private static final String ROT_Y = "rotY";
//    private static final String ROT_Z = "rotZ";
//
//
////    private static final String SCALE_X = "scaleX";
////    private static final String SCALE_Y = "scaleY";
////    private static final String SCALE_Z = "scaleZ";
//
//    /**
//     * The global position of this object (mutable).
//     */
//    @Getter
//    private final Vector3d position = new Vector3d();
//
//    /**
//     * The rotation of this object
//     */
//    @Getter
//    private final Vector3d rotation = new  Vector3d();
//
////    /**
////     * The YXZ euler rotation of this object in degrees (mutable).
////     */
////    @Getter
////    private final Vector3d rotation = new Vector3d();
//
//    /**
//     * The scale of this object (mutable).
//     */
//    @Getter
//    private final Vector3d scale = new Vector3d(1, 1, 1);
//
//    public void setPosition(Vector3dc pos) {
//        position.set(pos);
//    }
//
//    public void setPosition(double x, double y, double z) {
//        position.set(x, y, z);
//    }
//
//    public void setPosition(Vec3d vec) {
//        position.set(vec.getX(), vec.getY(), vec.getZ());
//    }
//
//
//    public ReplayObject3DOld(ReplayObjectType<?> type, ReplayScene scene) {
//        super(type, scene);
//
//        addProperty(POS_X,  position::x, v -> position.x = v);
//        addProperty(POS_Y,  position::y, v -> position.y = v);
//        addProperty(POS_Z,  position::z, v -> position.z = v);
//
//        addProperty(ROT_W,  rotation::w, v -> rotation.w = v);
//        addProperty(ROT_X,  rotation::x, v -> rotation.x = v);
//        addProperty(ROT_Y,  rotation::y, v -> rotation.y = v);
//        addProperty(ROT_Z,  rotation::z, v -> rotation.z = v);
//
//
////        addProperty(SCALE_X, scale::x,    v -> scale.x = v);
////        addProperty(SCALE_Y, scale::y,    v -> scale.y = v);
////        addProperty(SCALE_Z, scale::z,    v -> scale.z = v);
//    }
//
//    protected abstract boolean hasPosition();
//    protected abstract boolean hasRotation();
//    protected abstract boolean hasScale();
//
//    @Override
//    public Transform3 getTransform(Transform3 dest) {
//        return getBaseTransform(dest);
//    }
//
//    /**
//     * Get the base transform of this replay object from to its properties, ignoring all modifiers.
//     * @param dest Will hold the result.
//     * @return <code>dest</code>
//     */
//    public final Transform3 getBaseTransform(Transform3 dest) {
//        dest.identity();
//        dest.rotScale()
//                // Joml uses right-handed coordinates
//                .rotate(new Quaternionf().rotateYXZ(
//                        (float) -Math.toRadians(rotation.y),
//                        (float) Math.toRadians(rotation.x),
//                        (float) Math.toRadians(rotation.z)))
//                .scale((float) scale.x, (float) scale.y, (float) scale.z);
//        dest.pos().set(position);
//        return dest;
//    }
//
//    public final Matrix4f getBaseTransformMatrix(double centerX, double centerY, double centerZ, Matrix4f dest) {
//        dest.identity();
//        dest.setRotationYXZ(
//                (float) -Math.toRadians(rotation.y),
//                (float) Math.toRadians(rotation.x),
//                (float) Math.toRadians(rotation.z)
//        );
////        dest.scale((float) scale.x, (float) scale.y, (float) scale.z);
//
//        dest.setTranslation((float) (position.x - centerX), (float) (position.y - centerY), (float) (position.z - centerZ));
//        return dest;
//    }
//
//    public Matrix4f getBaseTransformMatrix(Vector3dc center, Matrix4f dest) {
//        return getBaseTransformMatrix(center.x(), center.y(), center.z(), dest);
//    }
//
//    public final void setBaseTransformMatrix(double centerX, double centerY, double centerZ, Matrix4fc matrix) {
//        getMTranslation(matrix, position).add(centerX, centerY, centerZ);
//        rotation.set(MathUtils.entityRot(matrix.getNormalizedRotation(new Quaternionf())));
//        getMScale(matrix, scale);
//    }
//
//    public final void setBaseTransformMatrix(Vector3dc center, Matrix4fc matrix) {
//        setBaseTransformMatrix(center.x(), center.y(), center.z(), matrix);
//    }
//
//    public void setBaseTransform(Transform3 transform) {
//        position.set(transform.pos());
//        rotation.set(MathUtils.entityRot(transform.getRot(new Quaternionf())));
//        scale.set(transform.getScale(new Vector3f()));
//    }
//
//
//    @Override
//    public void onCreated() {
//        // Place in front of the player for convenience
//        var camera = MinecraftClient.getInstance().cameraEntity;
//        if (camera == null) {
//            return;
//        }
//
//        Vec3d pos = camera.getCameraPosVec(0);
//        Vec3d normal = camera.getRotationVec(0);
//
//        Vec3d spawnPos = pos.add(normal.multiply(5));
//
//        position.set(spawnPos.x, spawnPos.y, spawnPos.z);
//    }
//
//    @Override
//    protected void writeJson(JsonObject json, JsonSerializationContext context) {
//        if (hasPosition()) {
//            json.add("position", writeJsonVec(position));
//        }
//        if (hasRotation()) {
//            json.add("rotation", writeJsonVec(rotation));
//        }
//        if (hasScale()) {
//            json.add("scale", writeJsonVec(scale));
//        }
//
//    }
//
//    @Override
//    protected void readJson(JsonObject json, JsonDeserializationContext context) {
//        if (json.has("position")) {
//            readJsonVec(json.getAsJsonArray("position"), position);
//        } else {
//            position.set(0, 0, 0);
//        }
//        if (json.has("rotation")) {
//            readJsonVec(json.getAsJsonArray("rotation"), rotation);
//        } else {
//            rotation.set(0, 0, 0);
//        }
//        if (json.has("scale")) {
//            readJsonVec(json.getAsJsonArray("scale"), scale);
//        } else {
//            scale.set(1, 1, 1);
//        }
//    }
//
//
//    private static JsonArray writeJsonVec(Vector3dc vec) {
//        JsonArray arr = new JsonArray();
//        arr.add(vec.x());
//        arr.add(vec.y());
//        arr.add(vec.z());
//        return arr;
//    }
//
//    private static void readJsonVec(JsonArray arr, Vector3d dest) {
//        dest.x = arr.get(0).getAsDouble();
//        dest.y = arr.get(1).getAsDouble();
//        dest.z = arr.get(2).getAsDouble();
//    }
//
//    public void addPositionKeyframe(int timestamp, Vector3dc value) {
//        getOrCreateChannel(POS_X).addKeyframe(timestamp, value.x());
//        getOrCreateChannel(POS_Y).addKeyframe(timestamp, value.y());
//        getOrCreateChannel(POS_Z).addKeyframe(timestamp, value.z());
//    }
//
//    public void addRotationKeyframe(int timestamp, Vector3dc value) {
//        getOrCreateChannel(ROT_X).addKeyframe(timestamp, value.x());
//        getOrCreateChannel(ROT_Y).addKeyframe(timestamp, value.y());
//        getOrCreateChannel(ROT_Z).addKeyframe(timestamp, value.z());
//    }
//
//    public void addScaleKeyframe(int timestamp, Vector3dc value) {
//        getOrCreateChannel(SCALE_X).addKeyframe(timestamp, value.x());
//        getOrCreateChannel(SCALE_Y).addKeyframe(timestamp, value.y());
//        getOrCreateChannel(SCALE_Z).addKeyframe(timestamp, value.z());
//    }
//
//    @Override
//    public boolean insertKey(int timestamp) {
//        boolean success = false;
//        if (hasPosition()) {
//            addPositionKeyframe(timestamp, getPosition());
//            success = true;
//        }
//
//        if (hasRotation()) {
//            addRotationKeyframe(timestamp, getRotation());
//            success = true;
//        }
//
//        if (hasScale()) {
//            addScaleKeyframe(timestamp, getScale());
//            success = true;
//        }
//
//        return success;
//    }
//
////    boolean wasDragging = false;
//    boolean startedDragging = false;
//
//    @Override
//    public PropertiesPanelState drawPropertiesPanel(EditorState editor) {
//        int pHead = editor.getPlayhead();
//        boolean modified = false;
//        modified |= hasPosition() && dragFloatN("Position", .125f, pHead, POS_X, POS_Y, POS_Z);
//        modified |= hasRotation() && dragFloatN("Rotation", 1, pHead, ROT_X, ROT_Y, ROT_Z);
//        modified |= hasScale() && dragFloatN("Scale", 1, pHead, SCALE_X, SCALE_Y, SCALE_Z);
//
//        if (modified || (startedDragging && ImGui.isMouseDown(0))) {
//            startedDragging = true;
//            return PropertiesPanelState.DRAGGING;
//        } else if (startedDragging) {
//            startedDragging = false;
//            return PropertiesPanelState.COMMIT;
//        } else {
//            return PropertiesPanelState.NONE;
//        }
//    }
//
//    // Wrapper to reduce code bloat
//    private boolean dragFloatN(String name, float speed, int playhead, String... properties) {
//        var state = PropertyWidgets.dragFloatN(this, name, speed, playhead, properties);
//        return state.isUpdated() || state.hasNewKey();
//    }
//
//
//    private boolean wasDragging;
//    private final Matrix4f dragStartMatrix = new Matrix4f();
//
//    @Override
//    public PropertiesPanelState drawGizmos(EditorState editor, Vector3dc cameraPos, Matrix4fc viewMatrix, Matrix4fc projectionMatrix, boolean hideUI) {
//        if (hideUI || !editor.isObjectActive(getId())) return PropertiesPanelState.NONE;
//
//
//        Matrix4f modelMatrix = getBaseTransformMatrix(cameraPos, new Matrix4f());
//        if (!wasDragging) {
//            dragStartMatrix.set(modelMatrix);
//        }
//        int operation = 0;
//        if (editor.showGizmoPos() && hasPosition()) operation |= Operation.TRANSLATE;
//        if (editor.showGizmoRot() && hasRotation()) operation |= Operation.ROTATE;
//        if (editor.showGizmoScale() && hasScale()) operation |= Operation.SCALE;
//
//        GizmoRenderer.manipulate(viewMatrix, projectionMatrix, operation,
//                editor.isLocalGizmos() ? Mode.LOCAL : Mode.WORLD, modelMatrix);
//
//        if (ImGuizmo.isUsing()) {
//            wasDragging = true;
//            setBaseTransformMatrix(cameraPos, modelMatrix);
//            return PropertiesPanelState.DRAGGING;
//        } else if (wasDragging) {
//            boolean commit = !dragStartMatrix.equals(modelMatrix, .01f);
//            wasDragging = false;
//            return commit ? PropertiesPanelState.COMMIT : PropertiesPanelState.NONE;
//        }
//        return PropertiesPanelState.NONE;
//
//    }
//
//    /**
//     * Re-implementation of {@link Matrix4f#getTranslation}
//     */
//    private static Vector3d getMTranslation(Matrix4fc m, Vector3d dest) {
//        dest.x = m.m30();
//        dest.y = m.m31();
//        dest.z = m.m32();
//        return dest;
//    }
//
//    /**
//     * Re-implementation of {@link Matrix4f#getScale}
//     */
//    private static Vector3d getMScale(Matrix4fc m, Vector3d dest) {
//        dest.x = Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
//        dest.y = Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
//        dest.z = Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
//        return dest;
//    }
//
//    private static final int POS_MASK = Operation.TRANSLATE_X | Operation.TRANSLATE_Y | Operation.TRANSLATE_Z;
//    private static final int ROT_MASK = Operation.ROTATE_X | Operation.ROTATE_Y | Operation.ROTATE_Z | Operation.ROTATE_SCREEN;
//    private static final int SCALE_MASK = Operation.SCALE_X | Operation.SCALE_Y | Operation.SCALE_Z
//            | Operation.BOUNDS | Operation.SCALE_XU | Operation.SCALE_YU | Operation.SCALE_ZU;
//
//    private static int filterBitFlags(int in, boolean hasPos, boolean hasRot, boolean hasScale) {
//        int mask = (hasPos ? POS_MASK : 0)
//                | (hasRot ? ROT_MASK : 0)
//                | (hasScale ? SCALE_MASK : 0);
//        return in & mask;
//    }
//
//    @Override
//    public int getChannelColor(String chName) {
//        int RED = 0xFF0000FF;
//        int GREEN = 0xFF00FF00;
//        int BLUE = 0xFFFF6600;
//
//        return switch(chName) {
//            case POS_X, ROT_X, SCALE_X -> RED;
//            case POS_Y, ROT_Y, SCALE_Y -> GREEN;
//            case POS_Z, ROT_Z, SCALE_Z -> BLUE;
//            default -> super.getChannelColor(chName);
//        };
//    }
//}
