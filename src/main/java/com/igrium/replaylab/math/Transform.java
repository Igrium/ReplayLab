package com.igrium.replaylab.math;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.joml.*;

/**
 * A three-dimensional transform that supports large positional coordinates.
 */
@Accessors(fluent = true)
@Getter
public class Transform {
    private final Vector3d position;
    private final Quaternionf rotation;
    private float scale;

    public Transform() {
        position = new Vector3d();
        rotation = new Quaternionf();
        scale = 1;
    }

    public Transform(Vector3dc position, Quaternionfc rotation, float scale) {
        this.position = new Vector3d(position);
        this.rotation = new Quaternionf(rotation);
        this.scale = scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * Apply a child transform on top of this one.
     *
     * @param child Child transform to apply.
     * @param dest  Destination transform
     * @return <code>dest</code>
     */
    public Transform mul(Transform child, Transform dest) {
        // Snapshot inputs first so dest may alias this or child safely.
        float parentRotX = this.rotation.x, parentRotY = this.rotation.y, parentRotZ = this.rotation.z, parentRotW = this.rotation.w;
        float childRotX = child.rotation.x, childRotY = child.rotation.y, childRotZ = child.rotation.z, childRotW = child.rotation.w;

//        float parentScaleX = this.scale.x, parentScaleY = this.scale.y, parentScaleZ = this.scale.z;
        float parentScale = this.scale;

        double parentPosX = this.position.x, parentPosY = this.position.y, parentPosZ = this.position.z;
        double childPosX = child.position.x, childPosY = child.position.y, childPosZ = child.position.z;

        // childPos in parent space, then rotated into world space
        double scaledOffsetX = parentScale * childPosX;
        double scaledOffsetY = parentScale * childPosY;
        double scaledOffsetZ = parentScale * childPosZ;

        // world position = parent position + parent rotation * (parent scale * child position)
        dest.rotation.set(parentRotX, parentRotY, parentRotZ, parentRotW);
        dest.rotation.transform(scaledOffsetX, scaledOffsetY, scaledOffsetZ, dest.position);
        dest.position.add(parentPosX, parentPosY, parentPosZ);

        // world rotation = parent rotation * child rotation
        dest.rotation.mul(childRotX, childRotY, childRotZ, childRotW);


        dest.scale = parentScale * child.scale;

        return dest;
    }

    /**
     * Modify this transform with a child transform on top of it.
     *
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
        dest.scale = scale;
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
        dest.scale = scale;
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
        dest.scale = scale;
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

//    public Transform scale(float scaleX, float scaleY, float scaleZ, Transform dest) {
//        dest.position.set(this.position);
//        dest.rotation.set(this.rotation);
//        this.scale.mul(scaleX, scaleY, scaleZ, dest.scale);
//        return dest;
//    }
//
//    public Transform scale(Vector3fc scale, Transform dest) {
//        return scale(scale.x(), scale.y(), scale.z(), dest);
//    }

    public Transform scale(float amount, Transform dest) {
        dest.rotation.set(this.rotation);
        dest.position.set(this.position);
        dest.scale = this.scale * amount;
        return dest;
    }

    public Transform scale(float amount) {
        return scale(amount, this);
    }

    public Transform scaleAround(float factor, double pivotX, double pivotY, double pivotZ, Transform dest) {
        double offsetX = position.x - pivotX;
        double offsetY = position.y - pivotY;
        double offsetZ = position.z - pivotZ;

        dest.position.set(pivotX + offsetX * factor, pivotY, pivotZ + offsetZ * factor);
        dest.scale = this.scale * factor;
        dest.rotation.set(this.rotation);
        return dest;
    }

    public Transform scaleAround(float factor, double pivotX, double pivotY, double pivotZ) {
        return scaleAround(factor, pivotX, pivotY, pivotZ, this);
    }

    public Transform scaleAround(float factor, Vector3dc pivot, Transform dest) {
        return scaleAround(factor, pivot.x(), pivot.y(), pivot.z(), dest);
    }

    public Transform scaleAround(float factor, Vector3dc pivot) {
        return scaleAround(factor, pivot, this);
    }

    public void invert(Transform dest) {
        rotation.invert(dest.rotation);
        dest.rotation.transform(
                -position.x / scale,
                -position.y / scale,
                -position.z / scale,
                dest.position
        );

        dest.scale = 1 / scale;
    }

    public void invert() {
        invert(this);
    }
}
