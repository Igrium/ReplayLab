package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
import imgui.ImGui;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.joml.Math;

import java.util.HashSet;
import java.util.Set;

/**
 * An object with a 3-dimensional transform
 */
public abstract class ReplayObject3D extends ReplayObject implements TransformProvider {

    private static final String POS_X = "posX";
    private static final String POS_Y = "posY";
    private static final String POS_Z = "posZ";

    private static final String ROT_X = "rotX";
    private static final String ROT_Y = "rotY";
    private static final String ROT_Z = "rotZ";

    private static final String SCALE_X = "scaleX";
    private static final String SCALE_Y = "scaleY";
    private static final String SCALE_Z = "scaleZ";

    /**
     * The global position of this object (mutable).
     */
    @Getter
    private final Vector3d position = new Vector3d();

    /**
     * The YXZ euler rotation of this object in degrees (mutable).
     */
    @Getter
    private final Vector3d rotation = new Vector3d();

    /**
     * The scale of this object (mutable).
     */
    @Getter
    private final Vector3d scale = new Vector3d(1, 1, 1);

    /**
     * The name of this object's parent object.
     */
    @Getter @Setter
    private @Nullable String parent;

    /**
     * Find this object's parent in the scene, cast it to a TransformProvider, and return it.
     * @return Parent object; <code>null</code> if the object could not be found, or it's not a TransformProvider
     */
    public @Nullable TransformProvider getParentObject() {
        if (parent == null)
            return null;

        var obj = getScene().getObject(parent);
        if (obj instanceof TransformProvider t) {
            return t;
        } else {
            return null;
        }
    }

    public ReplayObject3D(ReplayObjectType<?> type, ReplayScene scene) {
        super(type, scene);

        addProperty(POS_X,  position::x, v -> position.x = v);
        addProperty(POS_Y,  position::y, v -> position.y = v);
        addProperty(POS_Z,  position::z, v -> position.z = v);

        addProperty(ROT_X,  rotation::x, v -> rotation.x = v);
        addProperty(ROT_Y,  rotation::y, v -> rotation.y = v);
        addProperty(ROT_Z,  rotation::z, v -> rotation.z = v);

        addProperty(SCALE_X, scale::x,    v -> scale.x = v);
        addProperty(SCALE_Y, scale::y,    v -> scale.y = v);
        addProperty(SCALE_Z, scale::z,    v -> scale.z = v);
    }

    protected abstract boolean hasPosition();
    protected abstract boolean hasRotation();
    protected abstract boolean hasScale();

    private static final ThreadLocal<Set<ReplayObject3D>> parentStack = ThreadLocal.withInitial(HashSet::new);

    @Override
    public final void getCombinedTransform(Matrix4d dest) {
        var parent = getParentObject();
        // Prevent recursion
        if (parent != null && parentStack.get().add(this)) {
            try {
                parent.getCombinedTransform(dest);
            } finally {
                parentStack.get().remove(this);
            }
        }
        getLocalTransform(dest);
    }

    public final void getLocalTransform(Matrix4d dest) {
        dest.translate(position);
        dest.rotateYXZ(Math.toRadians(rotation.x), Math.toRadians(rotation.y), Math.toRadians(rotation.z));
        dest.scale(scale);
    }

    /**
     * Get the final transform of this object in timeline-compatible components. Includes parents constraints
     *
     * @param outPos   The global position of this object
     * @param outRot   The YXZ euler rotation of this object in degrees
     * @param outScale The scale of this object
     */
    public final void getCombinedTransform(@Nullable Vector3d outPos, @Nullable Vector3d outRot, @Nullable Vector3d outScale) {
        if (parent == null) {
            if (outPos != null) outPos.set(position);
            if (outRot != null) outRot.set(rotation);
            if (outScale != null) outScale.set(scale);
            return;
        }

        Matrix4d m = new Matrix4d();
        getCombinedTransform(m);

        if (outPos != null) m.getTranslation(outPos);
        if (outScale != null) m.getScale(outScale);

        if (outRot != null) {
            Quaterniond rot = m.getNormalizedRotation(new Quaterniond());
            rot.getEulerAnglesYXZ(outRot);
            rot.x = Math.toDegrees(rot.x);
            rot.y = Math.toDegrees(rot.y);
            rot.z = Math.toDegrees(rot.z);
        }
    }

    @Override
    protected void writeJson(JsonObject json, JsonSerializationContext context) {
        if (hasPosition()) {
            json.add("position", writeJsonVec(position));
        }
        if (hasRotation()) {
            json.add("rotation", writeJsonVec(rotation));
        }
        if (hasScale()) {
            json.add("scale", writeJsonVec(scale));
        }

        if (parent != null) {
            json.addProperty("parent", getParent());
        }
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("position")) {
            readJsonVec(json.getAsJsonArray("position"), position);
        } else {
            position.set(0, 0, 0);
        }
        if (json.has("rotation")) {
            readJsonVec(json.getAsJsonArray("rotation"), rotation);
        } else {
            rotation.set(0, 0, 0);
        }
        if (json.has("scale")) {
            readJsonVec(json.getAsJsonArray("scale"), scale);
        } else {
            scale.set(1, 1, 1);
        }

        if (json.has("parent")) {
            setParent(json.getAsJsonArray("parent").getAsString());
        }
    }


    private static JsonArray writeJsonVec(Vector3dc vec) {
        JsonArray arr = new JsonArray();
        arr.add(vec.x());
        arr.add(vec.y());
        arr.add(vec.z());
        return arr;
    }

    private static void readJsonVec(JsonArray arr, Vector3d dest) {
        dest.x = arr.get(0).getAsDouble();
        dest.y = arr.get(1).getAsDouble();
        dest.z = arr.get(2).getAsDouble();
    }

    public void addPositionKeyframe(int timestamp, Vector3dc value) {
        getOrCreateChannel(POS_X).addKeyframe(timestamp, value.x());
        getOrCreateChannel(POS_Y).addKeyframe(timestamp, value.y());
        getOrCreateChannel(POS_Z).addKeyframe(timestamp, value.z());
    }

    public void addRotationKeyframe(int timestamp, Vector3dc value) {
        getOrCreateChannel(ROT_X).addKeyframe(timestamp, value.x());
        getOrCreateChannel(ROT_Y).addKeyframe(timestamp, value.y());
        getOrCreateChannel(ROT_Z).addKeyframe(timestamp, value.z());
    }

    public void addScaleKeyframe(int timestamp, Vector3dc value) {
        getOrCreateChannel(SCALE_X).addKeyframe(timestamp, value.x());
        getOrCreateChannel(SCALE_Y).addKeyframe(timestamp, value.y());
        getOrCreateChannel(SCALE_Z).addKeyframe(timestamp, value.z());
    }

    @Override
    public boolean insertKey(int timestamp) {
        boolean success = false;
        if (hasPosition()) {
            addPositionKeyframe(timestamp, getPosition());
            success = true;
        }

        if (hasRotation()) {
            addRotationKeyframe(timestamp, getRotation());
            success = true;
        }

        if (hasScale()) {
            addScaleKeyframe(timestamp, getScale());
            success = true;
        }

        return success;
    }

//    boolean wasDragging = false;
    boolean startedDragging = false;

    @Override
    public PropertiesPanelState drawPropertiesPanel() {
        boolean modified = hasPosition() && inputVec3("Position", position, .125f);

        if (hasRotation() && inputVec3("Rotation", rotation, 1)) {
            modified = true;
        }

        if (hasScale() && inputVec3("Scale", scale, .25f)) {
            modified = true;
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

//
//        if (modified) {
//            wasDragging = true;
//            return PropertiesPanelState.DRAGGING;
//        } else if (wasDragging) {
//            wasDragging = false;
//            return PropertiesPanelState.COMMIT;
//        } else {
//            return PropertiesPanelState.NONE;
//        }

//        if (modified) {
//            wasDragging = true;
//        }
//        if (!modified && wasDragging) {
//            return PropertiesPanelState.DRAGGING;
//        } else if (modified) {
//            return PropertiesPanelState.COMMIT;
//        } else {
//            return PropertiesPanelState.NONE;
//        }
    }

    private static final float[] vecCache = new float[3];

    private static boolean inputVec3(String label, Vector3d vec, float speed){
        vecCache[0] = (float) vec.x;
        vecCache[1] = (float) vec.y;
        vecCache[2] = (float) vec.z;

        if (ImGui.dragFloat3(label, vecCache, speed)) {
            vec.set(vecCache);
            return true;
        }
        return false;
    }


}
