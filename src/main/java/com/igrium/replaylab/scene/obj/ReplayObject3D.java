package com.igrium.replaylab.scene.obj;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.scene.ReplayScene;
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

        addProperty("posX",  position::x, v -> position.x = v);
        addProperty("posY",  position::y, v -> position.y = v);
        addProperty("posZ",  position::z, v -> position.z = v);

        addProperty("rotX",  rotation::x, v -> rotation.x = v);
        addProperty("rotY",  rotation::y, v -> rotation.y = v);
        addProperty("rotZ",  rotation::z, v -> rotation.z = v);

        addProperty("scaleX", scale::x,    v -> scale.x = v);
        addProperty("scaleY", scale::y,    v -> scale.y = v);
        addProperty("scaleZ", scale::z,    v -> scale.z = v);
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

}
