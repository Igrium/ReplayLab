package com.igrium.replaylab.math;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.igrium.replaylab.math.DynamicRotation.RotationMode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.IOException;

class DynamicRotationSerializer extends TypeAdapter<DynamicRotation> {

    @Override
    public void write(JsonWriter jsonWriter, DynamicRotation rot) throws IOException {
        jsonWriter.beginObject();

        jsonWriter.name("mode").value(rot.mode().name());

        // Both forms get written; setMode(mode, false) lets the inactive one hold meaningful state.
        jsonWriter.name("quat");
        jsonWriter.beginArray();
        jsonWriter.value(rot.quaternion().x);
        jsonWriter.value(rot.quaternion().y);
        jsonWriter.value(rot.quaternion().z);
        jsonWriter.value(rot.quaternion().w);
        jsonWriter.endArray();

        jsonWriter.name("euler");
        jsonWriter.beginArray();
        jsonWriter.value(rot.euler().x);
        jsonWriter.value(rot.euler().y);
        jsonWriter.value(rot.euler().z);
        jsonWriter.endArray();

        jsonWriter.endObject();
    }

    @Override
    public DynamicRotation read(JsonReader jsonReader) throws IOException {
        DynamicRotation rot = new DynamicRotation();
        read(jsonReader, rot);
        return rot;
    }

    /**
     * Read a rotation into an existing instance, rather than allocating a new one.
     *
     * @param jsonReader Reader to read from.
     * @param dest       Will hold the result.
     * @return <code>dest</code>
     */
    public DynamicRotation read(JsonReader jsonReader, DynamicRotation dest) throws IOException {
        jsonReader.beginObject();

        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            switch (name) {
                case "mode": {
                    dest.setMode(RotationMode.valueOf(jsonReader.nextString()), false);
                    break;
                }
                case "quat": {
                    jsonReader.beginArray();
                    float x = (float) jsonReader.nextDouble();
                    float y = (float) jsonReader.nextDouble();
                    float z = (float) jsonReader.nextDouble();
                    float w = (float) jsonReader.nextDouble();
                    jsonReader.endArray();
                    dest.quaternion().set(x, y, z, w);
                    break;
                }
                case "euler": {
                    jsonReader.beginArray();
                    float x = (float) jsonReader.nextDouble();
                    float y = (float) jsonReader.nextDouble();
                    float z = (float) jsonReader.nextDouble();
                    jsonReader.endArray();
                    dest.euler().set(x, y, z);
                    break;
                }
                default:
                    jsonReader.skipValue();
                    break;
            }
        }

        jsonReader.endObject();

        return dest;
    }
}

/**
 * A simple class that can store rotation in a number of different formats.
 * <p>
 * Values stay in the form they were authored in where the math allows, falling back to a quaternion
 * round-trip when it doesn't. A round-trip wraps euler angles into <code>[-PI, PI]</code>, losing any
 * winding past a full turn.
 */
@JsonAdapter(DynamicRotationSerializer.class)
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class DynamicRotation {
    public enum RotationMode {
        QUATERNION, EULER_XYZ, EULER_ZYX, EULER_YXZ;

        /**
         * Get the translation key of this rotation mode's label
         */
        public String getLabel() {
            return "rot_mode." + name().toLowerCase();
        }
    }

    /**
     * Stores the rotation if we're in quaternion form.
     */
    @Getter
    private final Quaternionf quaternion = new Quaternionf();

    /**
     * Stores the rotation if we're in euler form
     */
    @Getter
    private final Vector3f euler = new Vector3f();

    @Getter
    private RotationMode mode = RotationMode.QUATERNION;

    public DynamicRotation set(@NonNull DynamicRotation other) {
        this.quaternion.set(other.quaternion);
        this.euler.set(other.euler);
        this.mode = other.mode;
        return this;
    }

    /**
     * Reset this rotation to identity, leaving the rotation mode alone.
     *
     * @return <code>this</code>
     */
    public DynamicRotation identity() {
        quaternion.identity();
        euler.zero();
        return this;
    }

    /**
     * Change the rotation mode in use.
     *
     * @param mode    The new rotation mode.
     * @param convert Whether to convert the old rotation to the new one.
     */
    public void setMode(RotationMode mode, boolean convert) {
        if (mode == this.mode) return;

        Quaternionf current = getQuaternion(new Quaternionf());
        this.mode = mode;
        if (convert) {
            setQuaternion(current);
        }
    }

    /**
     * Change the rotation mode in use, converting the current value to the new mode.
     *
     * @param mode The new rotation mode.
     */
    public void setMode(RotationMode mode) {
        setMode(mode, true);
    }

    /**
     * Convert this rotation to a quaternion
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Quaternionf getQuaternion(Quaternionf dest) {
        return switch (mode) {
            case QUATERNION -> dest.set(quaternion);
            case EULER_XYZ -> dest.identity().rotateXYZ(euler.x, euler.y, euler.z);
            case EULER_ZYX -> dest.identity().rotateZYX(euler.z, euler.y, euler.x);
            case EULER_YXZ -> dest.identity().rotateYXZ(euler.y, euler.x, euler.z);
        };
    }

    /**
     * Set the value of this rotation from a quaternion
     *
     * @param quaternion Quaternion to use
     * @return <code>this</code>
     */
    public DynamicRotation setQuaternion(Quaternionfc quaternion) {
        switch (mode) {
            case QUATERNION -> quaternion.get(this.quaternion).normalize();
            case EULER_XYZ -> quaternion.getEulerAnglesXYZ(this.euler);
            case EULER_YXZ -> quaternion.getEulerAnglesYXZ(this.euler);
            case EULER_ZYX -> quaternion.getEulerAnglesZYX(this.euler);
        }
        return this;
    }

    /**
     * Convert this rotation to a rotation matrix. Builds the matrix straight from the euler angles when
     * we're in euler form, skipping the quaternion round-trip.
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Matrix3f getMatrix(Matrix3f dest) {
        return switch (mode) {
            case QUATERNION -> dest.set(quaternion);
            case EULER_XYZ -> dest.identity().rotateXYZ(euler.x, euler.y, euler.z);
            case EULER_ZYX -> dest.identity().rotateZYX(euler.z, euler.y, euler.x);
            case EULER_YXZ -> dest.identity().rotateYXZ(euler.y, euler.x, euler.z);
        };
    }

    /**
     * Set the value of this rotation from a rotation matrix. Any scale or shear in the matrix is discarded.
     *
     * @param mat Matrix to read.
     * @return <code>this</code>
     */
    public DynamicRotation setMatrix(Matrix3fc mat) {
        return setQuaternion(mat.getNormalizedRotation(new Quaternionf()));
    }

    /**
     * Apply this rotation to a point, in double precision.
     *
     * @param x    Point X
     * @param y    Point Y
     * @param z    Point Z
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Vector3d transform(double x, double y, double z, Vector3d dest) {
        return getQuaternion(new Quaternionf()).transform(x, y, z, dest);
    }

    /**
     * Apply this rotation to a point in-place, in double precision.
     *
     * @param point Point to rotate.
     * @return <code>point</code>
     */
    public Vector3d transform(Vector3d point) {
        return transform(point.x, point.y, point.z, point);
    }

    /**
     * Post-multiply this rotation by a quaternion and store the result in <code>dest</code>.
     * <code>dest</code> takes on this rotation's mode.
     *
     * @param q    Quaternion to rotate by.
     * @param dest Will hold the result.
     * @return <code>dest</code>
     */
    public DynamicRotation rotate(Quaternionfc q, DynamicRotation dest) {
        Quaternionf result = getQuaternion(new Quaternionf()).mul(q);
        dest.mode = mode;
        return dest.setQuaternion(result);
    }

    public DynamicRotation rotate(Quaternionfc q) {
        return rotate(q, this);
    }

    /**
     * Pre-multiply this rotation by a quaternion and store the result in <code>dest</code>.
     * <code>dest</code> takes on this rotation's mode.
     *
     * @param q    Quaternion to rotate by.
     * @param dest Will hold the result.
     * @return <code>dest</code>
     */
    public DynamicRotation rotateLocal(Quaternionfc q, DynamicRotation dest) {
        Quaternionf result = new Quaternionf(q).mul(getQuaternion(new Quaternionf()));
        dest.mode = mode;
        return dest.setQuaternion(result);
    }

    public DynamicRotation rotateLocal(Quaternionfc q) {
        return rotateLocal(q, this);
    }

    /**
     * Post-multiply this rotation by a rotation about the X axis.
     * Exact when X is the last axis applied by the current euler mode.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateX(float angle) {
        if (mode == RotationMode.EULER_ZYX) {
            euler.x += angle;
            return this;
        }
        return rotate(new Quaternionf().rotateX(angle));
    }

    /**
     * Post-multiply this rotation by a rotation about the Y axis.
     * Always goes through a quaternion; no euler mode ends on Y.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateY(float angle) {
        return rotate(new Quaternionf().rotateY(angle));
    }

    /**
     * Post-multiply this rotation by a rotation about the Z axis.
     * Exact when Z is the last axis applied by the current euler mode.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateZ(float angle) {
        if (mode == RotationMode.EULER_XYZ || mode == RotationMode.EULER_YXZ) {
            euler.z += angle;
            return this;
        }
        return rotate(new Quaternionf().rotateZ(angle));
    }

    /**
     * Pre-multiply this rotation by a rotation about the X axis.
     * Exact when X is the first axis applied by the current euler mode.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateLocalX(float angle) {
        if (mode == RotationMode.EULER_XYZ) {
            euler.x += angle;
            return this;
        }
        return rotateLocal(new Quaternionf().rotateX(angle));
    }

    /**
     * Pre-multiply this rotation by a rotation about the Y axis.
     * Exact when Y is the first axis applied by the current euler mode.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateLocalY(float angle) {
        if (mode == RotationMode.EULER_YXZ) {
            euler.y += angle;
            return this;
        }
        return rotateLocal(new Quaternionf().rotateY(angle));
    }

    /**
     * Pre-multiply this rotation by a rotation about the Z axis.
     * Exact when Z is the first axis applied by the current euler mode.
     *
     * @param angle Angle in radians.
     * @return <code>this</code>
     */
    public DynamicRotation rotateLocalZ(float angle) {
        if (mode == RotationMode.EULER_ZYX) {
            euler.z += angle;
            return this;
        }
        return rotateLocal(new Quaternionf().rotateZ(angle));
    }

    /**
     * Invert this rotation and store the result in <code>dest</code>.
     * <code>dest</code> takes on this rotation's mode.
     *
     * @param dest Will hold the result.
     * @return <code>dest</code>
     */
    public DynamicRotation invert(DynamicRotation dest) {
        Quaternionf result = getQuaternion(new Quaternionf()).invert();
        dest.mode = mode;
        return dest.setQuaternion(result);
    }

    public DynamicRotation invert() {
        return invert(this);
    }

    /**
     * Multiply this rotation by the supplied right rotation and store the result in <code>dest</code>.
     * The right rotation is applied first. <code>dest</code> takes on this rotation's mode.
     *
     * @param right The right operand.
     * @param dest  Will hold the result.
     * @return <code>dest</code>
     */
    public DynamicRotation mul(DynamicRotation right, DynamicRotation dest) {
        Quaternionf result = getQuaternion(new Quaternionf())
                .mul(right.getQuaternion(new Quaternionf()));
        dest.mode = mode;
        return dest.setQuaternion(result);
    }

    public DynamicRotation mul(DynamicRotation right) {
        return mul(right, this);
    }

    /**
     * Convert this rotation to XYZ euler
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Vector3f getEulerXYZ(Vector3f dest) {
        if (mode == RotationMode.EULER_XYZ) {
            euler.get(dest);
        } else {
            getQuaternion(new Quaternionf()).getEulerAnglesXYZ(dest);
        }
        return dest;
    }

    /**
     * Convert this rotation to ZYX euler
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Vector3f getEulerZYX(Vector3f dest) {
        if (mode == RotationMode.EULER_ZYX) {
            euler.get(dest);
        } else {
            getQuaternion(new Quaternionf()).getEulerAnglesZYX(dest);
        }
        return dest;
    }

    /**
     * Convert this rotation to YXZ euler
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Vector3f getEulerYXZ(Vector3f dest) {
        if (mode == RotationMode.EULER_YXZ) {
            euler.get(dest);
        } else {
            getQuaternion(new Quaternionf()).getEulerAnglesYXZ(dest);
        }
        return dest;
    }

    /**
     * Set the value of this rotation from XYZ euler angles.
     *
     * @param angleX X angle in radians
     * @param angleY Y angle in radians
     * @param angleZ Z angle in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerXYZ(float angleX, float angleY, float angleZ) {
        if (mode == RotationMode.EULER_XYZ) {
            euler.set(angleX, angleY, angleZ);
        } else {
            setQuaternion(new Quaternionf().rotateXYZ(angleX, angleY, angleZ));
        }
        return this;
    }

    /**
     * Set the value of this rotation from XYZ euler angles.
     *
     * @param angles Euler angles in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerXYZ(Vector3fc angles) {
        return setEulerXYZ(angles.x(), angles.y(), angles.z());
    }

    /**
     * Set the value of this rotation from ZYX euler angles.
     *
     * @param angleZ Z angle in radians
     * @param angleY Y angle in radians
     * @param angleX X angle in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerZYX(float angleZ, float angleY, float angleX) {
        if (mode == RotationMode.EULER_ZYX) {
            euler.set(angleX, angleY, angleZ);
        } else {
            setQuaternion(new Quaternionf().rotateZYX(angleZ, angleY, angleX));
        }
        return this;
    }

    /**
     * Set the value of this rotation from ZYX euler angles.
     *
     * @param angles Euler angles in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerZYX(Vector3fc angles) {
        return setEulerZYX(angles.z(), angles.y(), angles.x());
    }

    /**
     * Set the value of this rotation from YXZ euler angles.
     *
     * @param angleY Y angle in radians
     * @param angleX X angle in radians
     * @param angleZ Z angle in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerYXZ(float angleY, float angleX, float angleZ) {
        if (mode == RotationMode.EULER_YXZ) {
            euler.set(angleX, angleY, angleZ);
        } else {
            setQuaternion(new Quaternionf().rotateYXZ(angleY, angleX, angleZ));
        }
        return this;
    }

    /**
     * Set the value of this rotation from YXZ euler angles.
     *
     * @param angles Euler angles in radians
     * @return <code>this</code>
     */
    public DynamicRotation setEulerYXZ(Vector3fc angles) {
        return setEulerYXZ(angles.y(), angles.x(), angles.z());
    }

}