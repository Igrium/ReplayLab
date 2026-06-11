package com.igrium.replaylab.math;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A simple class that can store rotation in a number of different formats.
 */
@Accessors(fluent = true)
public class DynamicRotation {
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

    private final Quaternionf tmpQuat = new Quaternionf();

    /**
     * Change the rotation mode in use.
     *
     * @param mode    The new rotation mode.
     * @param convert Whether to convert the old rotation to the new one.
     */
    public void setMode(RotationMode mode, boolean convert) {
        if (mode == this.mode) return;

        getQuaternion(tmpQuat);
        this.mode = mode;
        if (convert) {
            setQuaternion(tmpQuat);
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
     * Convert this rotation to XYZ euler
     *
     * @param dest Will hold the result
     * @return <code>dest</code>
     */
    public Vector3f getEulerXYZ(Vector3f dest) {
        if (mode == RotationMode.EULER_XYZ) {
            euler.get(dest);
        } else {
            getQuaternion(tmpQuat).getEulerAnglesXYZ(dest);
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
            getQuaternion(tmpQuat).getEulerAnglesZYX(dest);
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
            getQuaternion(tmpQuat).getEulerAnglesYXZ(dest);
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
            setQuaternion(tmpQuat.identity().rotateXYZ(angleX, angleY, angleZ));
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
            setQuaternion(tmpQuat.identity().rotateZYX(angleZ, angleY, angleX));
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
            setQuaternion(tmpQuat.identity().rotateYXZ(angleY, angleX, angleZ));
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
