package com.igrium.replaylab.math;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.joml.*;

/**
 * A three-dimensional transform that supports large positional coordinates.
 */
@Accessors(fluent = true) @Getter
public class Transform {
    private final Vector3d position;
    private final Quaternionf rotation;
    private final Vector3f scale;

    public Transform() {
        position = new Vector3d();
        rotation = new Quaternionf();
        scale = new Vector3f(1, 1, 1);
    }

    public Transform(Vector3dc position, Quaternionfc rotation, Vector3fc scale) {
        this.position = new Vector3d(position);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
    }

    public Transform(Vector3d position, Quaternionf rotation, float scale) {
        this.position = new Vector3d(position);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3f(scale);
    }

    /**
     * Apply a child transform on top of this one.
     * @param child Child transform to apply.
     * @param dest Destination transform
     * @return <code>dest</code>
     */
    public Transform mul(Transform child, Transform dest) {
        // Snapshot all inputs as primitives before writing anything to dest.
        // Without this, dest == this or dest == child corrupts reads mid-calculation.
        float parentRotX = this.rotation.x, parentRotY = this.rotation.y, parentRotZ = this.rotation.z, parentRotW = this.rotation.w;
        float childRotX = child.rotation.x, childRotY = child.rotation.y, childRotZ = child.rotation.z, childRotW = child.rotation.w;

        float parentScaleX = this.scale.x, parentScaleY = this.scale.y, parentScaleZ = this.scale.z;
        float childScaleX = child.scale.x, childScaleY = child.scale.y, childScaleZ = child.scale.z;

        double parentPosX = this.position.x, parentPosY = this.position.y, parentPosZ = this.position.z;
        double childPosX = child.position.x, childPosY = child.position.y, childPosZ = child.position.z;

        // Apply parent scale to child's local position to get the world-space offset.
        double scaledOffsetX = parentScaleX * childPosX;
        double scaledOffsetY = parentScaleY * childPosY;
        double scaledOffsetZ = parentScaleZ * childPosZ;

        // Load parent rotation into dest.rotation temporarily so we can rotate
        // the offset into world space — then add parent's world position.
        dest.rotation.set(parentRotX, parentRotY, parentRotZ, parentRotW);
        dest.rotation.transform(scaledOffsetX, scaledOffsetY, scaledOffsetZ, dest.position);
        dest.position.add(parentPosX, parentPosY, parentPosZ);

        // All reads are done. Safe to write the final rotation and scale.
        dest.rotation.mul(childRotX, childRotY, childRotZ, childRotW);
        dest.scale.set(parentScaleX * childScaleX, parentScaleY * childScaleY, parentScaleZ * childScaleZ);

        return dest;
    }

    /**
     * Modify this transform with a child transform on top of it.
     * @param child Child transform to apply.
     * @return <code>this</code>
     */
    public Transform mul(Transform child) {
        return mul(child, this);
    }

    public Transform premul(Transform parent, Transform dest) {
        return parent.mul(this, dest);
    }

    public Transform premul(Transform parent) {
        return parent.mul(this, this);
    }

    public Transform translate(double x, double y, double z, Transform dest) {
        dest.position.set(position.x + x, position.y + y, position.z + z);
        dest.rotation.set(rotation);
        dest.scale.set(scale);
        return dest;
    }

    public Transform translate(Vector3dc vec, Transform dest) {
        return translate(vec.x(), vec.y(), vec.z(), dest);
    }

    public Transform translate(double x, double y, double z) {
        position.add(x, y, z);
        return this;
    }

    public Transform translate(Vector3dc vec) {
        position.add(vec);
        return this;
    }

    public Transform rotate(Quaternionfc rotation, Transform dest) {
        rotation.mul(this.rotation, dest.rotation);
        dest.position.set(this.position);
        dest.scale.set(this.scale);
        return dest;
    }

    public Transform rotate(Quaternionfc rotation) {
        return rotate(rotation, this);
    }

    public Transform rotateAround(Quaternionfc rotation, double x, double y, double z, Transform dest) {
        double offsetX = position.x - x;
        double offsetY = position.y - y;
        double offsetZ = position.z - z;

        rotation.transform(offsetX, offsetY, offsetZ, dest.position);
        dest.position.add(x, y, z);
        rotation.mul(this.rotation, dest.rotation);
        dest.scale.set(this.scale);
        return dest;
    }

    public Transform rotateAround(Quaternionfc rotation, Vector3dc pivot, Transform dest) {
        return rotateAround(rotation, pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform rotateAround(Quaternionfc rotation, double x, double y, double z) {
        return rotateAround(rotation, x, y, z, this);
    }

    public Transform rotateAround(Quaternionfc rotation, Vector3dc pivot) {
        return rotateAround(rotation, pivot, this);
    }

    public Transform scale(float scaleX, float scaleY, float scaleZ, Transform dest) {
        this.position.mul(scaleX, scaleY, scaleZ, dest.position);
        this.scale.mul(scaleX, scaleY, scaleZ, dest.scale);
        dest.rotation.set(this.rotation);
        return dest;
    }

    public Transform scale(Vector3fc scale, Transform dest) {
        return scale(scale.x(), scale.y(), scale.z(), dest);
    }

    public Transform scale(float amount, Transform dest) {
        return scale(amount, amount, amount, dest);
    }

    public Transform scale(float scaleX, float scaleY, float scaleZ) {
        return scale(scaleX, scaleY, scaleZ, this);
    }

    public Transform scale(Vector3fc scale) {
        return scale(scale, this);
    }

    public Transform scale(float amount) {
        return scale(amount, amount, amount, this);
    }

    public Transform scaleAround(float scaleX, float scaleY, float scaleZ, double pivotX, double pivotY, double pivotZ, Transform dest) {
        double offsetX = position.x - pivotX;
        double offsetY = position.y - pivotY;
        double offsetZ = position.z - pivotZ;

        dest.position.set(pivotX + offsetX * scaleX, pivotY + offsetY * scaleY, pivotZ + offsetZ * scaleZ);
        this.scale.mul(scaleX, scaleY, scaleZ, dest.scale);
        dest.rotation.set(this.rotation);
        return dest;
    }

    public Transform scaleAround(float scaleX, float scaleY, float scaleZ, double pivotX, double pivotY, double pivotZ) {
        return scaleAround(scaleX, scaleY, scaleZ, pivotX, pivotY, pivotZ, this);
    }

    public Transform scaleAround(float scaleX, float scaleY, float scaleZ, Vector3dc pivot, Transform dest) {
        return scaleAround(scaleX, scaleY, scaleZ, pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform scaleAround(float scaleX, float scaleY, float scaleZ, Vector3dc pivot) {
        return scaleAround(scaleX, scaleY, scaleZ, pivot, this);
    }

    public Transform scaleAround(Vector3fc scale, Vector3dc pivot, Transform dest) {
        return scaleAround(scale.x(), scale.y(), scale.z(), pivot, dest);
    }

    public Transform scaleAround(Vector3fc scale, Vector3dc pivot) {
        return scaleAround(scale, pivot, this);
    }

    public void invert(Transform dest) {
        rotation.invert(dest.rotation);
        dest.rotation.transform(
                -position.x / scale.x,
                -position.y / scale.y,
                -position.z / scale.z,
                dest.position
        );
        dest.scale.set(1f / scale.x, 1f / scale.y, 1f / scale.z);
    }

    public void invert() {
        invert(this);
    }
}
