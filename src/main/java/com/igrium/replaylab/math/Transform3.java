package com.igrium.replaylab.math;

import org.joml.*;
import org.joml.Math;

/**
 * A mutable, three-dimensional, matrix-based transform that keeps a double-precision global offset.
 *
 * @param pos      Global positional root.
 * @param rotScale Rotation/Scale matrix
 */
public record Transform3(Vector3d pos, Matrix3f rotScale) {
    public Transform3(Vector3dc position, Matrix3fc matrix) {
        this(new Vector3d(position), new Matrix3f(matrix));
    }

    public Transform3(Vector3dc position) {
        this(position, new Matrix3f());
    }

    public Transform3(double x, double y, double z) {
        this(new Vector3d(x, y, z), new Matrix3f());
    }

    public Transform3() {
        this(new Vector3d(), new Matrix3f());
    }

    public Vector3d getPos(Vector3d dest) {
        return dest.set(pos);
    }

    public Quaternionf getRot(Quaternionf dest) {
        return rotScale.getNormalizedRotation(dest);
    }

    public Vector3f getScale(Vector3f dest) {
        return rotScale.getScale(dest);
    }

    public Transform3 set(Transform3 src) {
        pos.set(src.pos);
        rotScale.set(src.rotScale);
        return this;
    }

    public Transform3 set(Vector3dc pos, Matrix3fc srcRotScale) {
        this.pos.set(pos);
        rotScale.set(srcRotScale);
        return this;
    }

    public Transform3 set(Vector3dc pos, Quaternionfc rot) {
        identity();
        this.pos.set(pos);
        rotScale.set(rot);
        return this;
    }

    public Transform3 set(Vector3dc pos, Quaternionfc rot, Vector3fc scale) {
        return set(pos, rot).scale(scale);
    }

    public Transform3 identity() {
        pos.zero();
        rotScale.identity();
        return this;
    }

    public Transform3 invert(Transform3 dest) {
        rotScale.invert(dest.rotScale);

        transformDouble(dest.rotScale, -pos.x, -pos.y, -pos.z, dest.pos);
        return dest;
    }

    public Transform3 invert() {
        return invert(this);
    }

    /**
     * Multiply this transform by the supplied right transform and store the result in {@code dest}.
     * <p>
     * If {@code T} is this transform and {@code R} the right transform, then the new transform will
     * be {@code T * R}. So when transforming a point {@code p} with the new transform by using
     * {@code T * R * p}, the transformation of the right transform will be applied first!
     *
     * @param right the right operand of the transform multiplication
     * @param dest  will hold the result
     * @return dest
     */
    public Transform3 mul(Transform3 right, Transform3 dest) {
        // Cache to handle dest == this
        double px = pos.x, py = pos.y, pz = pos.z;

        transformDouble(rotScale, right.pos.x, right.pos.y, right.pos.z, dest.pos);
        dest.pos.add(px, py, pz);
        rotScale.mul(right.rotScale, dest.rotScale);

        return dest;
    }


    /**
     * Multiply this transform by the supplied right transform and store the result in {@code this}.
     * <p>
     * If {@code T} is this transform and {@code R} the right transform, then the new transform will
     * be {@code T * R}. So when transforming a point {@code p} with the new transform by using
     * {@code T * R * p}, the transformation of the right transform will be applied first!
     *
     * @param right the right operand of the transform multiplication
     * @return this
     */
    public Transform3 mul(Transform3 right) {
        return mul(right, this);
    }

    /**
     * Pre-multiply this transform by the supplied left transform and store the result in {@code dest}.
     * <p>
     * If {@code L} is the left transform and {@code T} is this transform, then the new transform will
     * be {@code L * T}. So when transforming a point {@code p} with the new transform by using
     * {@code L * T * p}, the transformation of this transform will be applied first!
     *
     * @param left the left operand of the transform multiplication
     * @param dest will hold the result
     * @return dest
     */
    public Transform3 preMul(Transform3 left, Transform3 dest) {
        return left.mul(this, dest);
    }

    /**
     * Pre-multiply this transform by the supplied left transform and store the result in {@code this}.
     * <p>
     * If {@code L} is the left transform and {@code T} is this transform, then the new transform will
     * be {@code L * T}. So when transforming a point {@code p} with the new transform by using
     * {@code L * T * p}, the transformation of this transform will be applied first!
     *
     * @param left the left operand of the transform multiplication
     * @return this
     */
    public Transform3 preMul(Transform3 left) {
        return left.mul(this, this);
    }

    /**
     * Translate this transform by a set amount and store the result in {@code dest}.
     *
     * @param x    X transform
     * @param y    Y transform
     * @param z    Z transform
     * @param dest will hold the result
     * @return dest
     */
    public Transform3 translate(double x, double y, double z, Transform3 dest) {
        dest.rotScale.set(rotScale);
        pos.add(x, y, z, dest.pos);
        return dest;
    }

    /**
     * Translate this transform by a set amount and store the result in {@code this}.
     *
     * @param x X transform
     * @param y Y transform
     * @param z Z transform
     * @return this
     */
    public Transform3 translate(double x, double y, double z) {
        pos.add(x, y, z);
        return this;
    }

    /**
     * Translate this transform by a set amount and store the result in {@code this}
     *
     * @param offset Vector to translate by.
     * @param dest   Will store the result.
     * @return dest
     */
    public Transform3 translate(Vector3dc offset, Transform3 dest) {
        dest.rotScale.set(rotScale);
        pos.add(offset, dest.pos);
        return dest;
    }

    /**
     * Translate this transform by a set amount and store the result in {@code dest}
     *
     * @param offset Vector to translate by.
     * @return this
     */
    public Transform3 translate(Vector3dc offset) {
        pos.add(offset);
        return this;
    }

    /**
     * Post-multiply the rotation by a quaternion (position unchanged) and store the result in {@code dest}.
     *
     * @param q    Quaternion to rotate by.
     * @param dest Will store the result.
     * @return dest
     */
    public Transform3 rotate(Quaternionfc q, Transform3 dest) {
        dest.pos.set(pos);
        rotScale.rotate(q, dest.rotScale);
        return dest;
    }

    /**
     * Post-multiply the rotation by a quaternion (position unchanged) and store the result in {@code this}.
     *
     * @param q Quaternion to rotate by.
     * @return this
     */
    public Transform3 rotate(Quaternionfc q) {
        return rotate(q, this);
    }

    /**
     * Pre-multiply the rotation by a quaternion - rotates in local space. Store the result in {@code dest}.
     *
     * @param q    Quaternion to rotate by.
     * @param dest Will hold the result.
     * @return dest
     */
    public Transform3 rotateLocal(Quaternionfc q, Transform3 dest) {
        dest.pos.set(pos);
        rotScale.rotateLocal(q, dest.rotScale);
        return dest;
    }

    /**
     * Pre-multiply the rotation by a quaternion - rotates in local space. Store the result in {@code this}.
     *
     * @param q Quaternion to rotate by.
     * @return this
     */
    public Transform3 rotateLocal(Quaternionfc q) {
        return rotateLocal(q, this);
    }

    public Transform3 rotateAround(Quaternionfc q, double pivotX, double pivotY, double pivotZ, Transform3 dest) {
        double localX = pos.x - pivotX;
        double localY = pos.y - pivotY;
        double localZ = pos.z - pivotZ;

        q.transform(localX, localY, localZ, dest.pos);
        dest.pos.add(pivotX, pivotY, pivotZ);
        rotScale.rotate(q, dest.rotScale);

        return dest;
    }

    public Transform3 rotateAround(Quaternionfc q, double pivotX, double pivotY, double pivotZ) {
        return rotateAround(q, pivotX, pivotY, pivotZ, this);
    }

    public Transform3 rotateAround(Quaternionfc q, Vector3dc pivot, Transform3 dest) {
        return rotateAround(q, pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform3 rotateAround(Quaternionfc q, Vector3dc pivot) {
        return rotateAround(q, pivot, this);
    }

    public Transform3 scaleAround(float x, float y, float z, double pivotX, double pivotY, double pivotZ, Transform3 dest) {
        dest.pos.x = (pos.x - pivotX) * x + pivotX;
        dest.pos.y = (pos.y - pivotY) * y + pivotY;
        dest.pos.z = (pos.z - pivotZ) * z + pivotZ;

        rotScale.scale(x, y, z, dest.rotScale);
        return dest;
    }

    public Transform3 scaleAround(float x, float y, float z, double pivotX, double pivotY, double pivotZ) {
        return scaleAround(x, y, z, pivotX, pivotY, pivotZ, this);
    }

    public Transform3 scaleAround(Vector3fc xyz, Vector3dc pivot, Transform3 dest) {
        return scaleAround(xyz.x(), xyz.y(), xyz.z(), pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform3 scaleAround(Vector3fc xyz, Vector3dc pivot) {
        return scaleAround(xyz, pivot, this);
    }

    public Transform3 scaleAround(float xyz, double pivotX, double pivotY, double pivotZ, Transform3 dest) {
        return scaleAround(xyz, xyz, xyz, pivotX, pivotY, pivotZ, dest);
    }

    public Transform3 scaleAround(float xyz, double pivotX, double pivotY, double pivotZ) {
        return scaleAround(xyz, pivotX, pivotY, pivotZ, this);
    }

    public Transform3 scaleAround(float xyz, Vector3dc pivot, Transform3 dest) {
        return scaleAround(xyz, pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform3 scaleAround(float xyz, Vector3dc pivot) {
        return scaleAround(xyz, pivot, this);
    }

    public Transform3 scale(float x, float y, float z, Transform3 dest) {
        dest.pos.set(pos);
        rotScale.scale(x, y, z, dest.rotScale);
        return dest;
    }

    public Transform3 scale(float x, float y, float z) {
        return scale(x, y, z, this);
    }

    public Transform3 scale(Vector3fc factor, Transform3 dest) {
        return scale(factor.x(), factor.y(), factor.z(), dest);
    }

    public Transform3 scale(Vector3fc factor) {
        return scale(factor, this);
    }

    public Transform3 scale(float xyz, Transform3 dest) {
        return scale(xyz, xyz, xyz, dest);
    }

    public Transform3 scale(float xyz) {
        return scale(xyz, this);
    }

    /**
     * Convert this Transform into a Matrix4f transformation,
     * relative to a root position to avoid floating-point precision loss.
     *
     * @param rootX Global root X
     * @param rootY Global root Y
     * @param rootZ Global root Z
     * @param dest  Will hold the result.
     * @return dest
     */
    public Matrix4f getMatrix(double rootX, double rootY, double rootZ, Matrix4f dest) {
        dest.identity().set3x3(rotScale).setTranslation(
                (float) (pos.x - rootX),
                (float) (pos.y - rootY),
                (float) (pos.z - rootZ)
        );
        return dest;
    }

    /**
     * Convert this Transform into a Matrix4f transformation,
     * relative to a root position to avoid floating-point precision loss.
     *
     * @param root Global root position
     * @param dest Will hold the result.
     * @return dest
     */
    public Matrix4f getMatrix(Vector3dc root, Matrix4f dest) {
        return getMatrix(root.x(), root.y(), root.z(), dest);
    }

    /**
     * Convert this Transform into a Matrix4f transformation.
     * @param dest Will hold the result.
     * @return dest
     * @implNote Could result in floating-point precision errors. See: {@link #getMatrix(Vector3dc, Matrix4f)}
     */
    public Matrix4f getMatrix(Matrix4f dest) {
        return getMatrix(0, 0, 0, dest);
    }

    /**
     * Re-implementation of Matrix3f's transform that uses double-precision
     */
    private static Vector3d transformDouble(Matrix3fc m, double x, double y, double z, Vector3d dest) {
        return dest.set(
                Math.fma(m.m00(), x, Math.fma(m.m10(), y, m.m20() * z)),
                Math.fma(m.m01(), x, Math.fma(m.m11(), y, m.m21() * z)),
                Math.fma(m.m02(), x, Math.fma(m.m12(), y, m.m22() * z)));
    }
}
