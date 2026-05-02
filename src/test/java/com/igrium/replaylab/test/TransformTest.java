package com.igrium.replaylab.test;

import org.joml.*;
import com.igrium.replaylab.math.Transform;
import org.joml.Math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Vibe-coded unit test. Bite me.
 */
class TransformTest {

    private static final float EPSILON = 1e-5f;
    private static final double EPSILON_D = 1e-9;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Asserts two transforms are component-wise equal within tolerance. */
    private static void assertTransformEquals(Transform expected, Transform actual) {
        assertEquals(expected.position().x, actual.position().x, TransformTest.EPSILON, "position.x");
        assertEquals(expected.position().y, actual.position().y, TransformTest.EPSILON, "position.y");
        assertEquals(expected.position().z, actual.position().z, TransformTest.EPSILON, "position.z");

        // Quaternions q and -q represent the same rotation; normalise sign before comparing.
        float dot = expected.rotation().dot(actual.rotation());
        assertTrue(Math.abs(dot) > 1f - TransformTest.EPSILON,
                () -> "rotation mismatch: expected " + expected.rotation() + " but got " + actual.rotation());

        // Updated for singular float scale
        assertEquals(expected.scale(), actual.scale(), TransformTest.EPSILON, "scale");
    }

    /** Returns a 90-degree rotation around the Y axis. */
    private static Quaternionf rotY90() {
        return new Quaternionf().rotateY((float) (Math.PI / 2));
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    @Test
    void defaultConstructor_isIdentity() {
        Transform t = new Transform();
        assertEquals(0.0, t.position().x, EPSILON_D);
        assertEquals(0.0, t.position().y, EPSILON_D);
        assertEquals(0.0, t.position().z, EPSILON_D);
        assertTrue(t.rotation().equals(new Quaternionf(), EPSILON));
        assertEquals(1f, t.scale(), EPSILON); // Singular scale check
    }

    @Test
    void fullConstructor_copiesComponents() {
        Vector3d pos = new Vector3d(1, 2, 3);
        Quaternionf rot = rotY90();
        float scale = 2.5f; // Singular float

        Transform t = new Transform(pos, rot, scale);

        assertEquals(pos.x, t.position().x, EPSILON_D);
        assertEquals(pos.y, t.position().y, EPSILON_D);
        assertEquals(pos.z, t.position().z, EPSILON_D);
        assertTrue(t.rotation().equals(rot, EPSILON));
        assertEquals(scale, t.scale(), EPSILON);
    }

    @Test
    void constructor_doesNotRetainInputReferences() {
        Vector3d pos = new Vector3d(1, 2, 3);
        Quaternionf rot = new Quaternionf();
        float scale = 1.0f;
        Transform t = new Transform(pos, rot, scale);

        pos.set(99, 99, 99);
        rot.set(1, 0, 0, 0);
        // Note: scale is a primitive float now, so it can't be "aliased" anyway.

        assertEquals(1.0, t.position().x, EPSILON_D, "position must be copied, not aliased");
        assertTrue(t.rotation().equals(new Quaternionf(), EPSILON), "rotation must be copied");
        assertEquals(1f, t.scale(), EPSILON);
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------

    @Test
    void translate_primitives_offsetsPosition() {
        Transform t = new Transform(new Vector3d(1, 2, 3), new Quaternionf(), 1f);
        t.translate(10, 20, 30);
        assertEquals(11.0, t.position().x, EPSILON_D);
        assertEquals(22.0, t.position().y, EPSILON_D);
        assertEquals(33.0, t.position().z, EPSILON_D);
    }

    @Test
    void translate_doesNotAffectRotationOrScale() {
        Quaternionf rot = rotY90();
        Transform t = new Transform(new Vector3d(), rot, 2.0f);
        t.translate(5, 0, 0);
        assertTrue(t.rotation().equals(rot, EPSILON));
        assertEquals(2.0f, t.scale(), EPSILON);
    }

    // -------------------------------------------------------------------------
    // Rotation
    // -------------------------------------------------------------------------

    @Test
    void rotate_doesNotAffectPositionOrScale() {
        Transform t = new Transform(new Vector3d(5, 10, 15), new Quaternionf(), 2.0f);
        t.rotate(rotY90());
        assertEquals(5.0, t.position().x, EPSILON_D);
        assertEquals(10.0, t.position().y, EPSILON_D);
        assertEquals(15.0, t.position().z, EPSILON_D);
        assertEquals(2.0f, t.scale(), EPSILON);
    }

    // -------------------------------------------------------------------------
    // Scale
    // -------------------------------------------------------------------------

    @Test
    void scale_uniform_multipliesScaleOnly() {
        // Note: As we discussed, scaling a Transform usually shouldn't move its position
        // unless you are scaling "Around" a pivot.
        Transform t = new Transform(new Vector3d(2, 4, 6), new Quaternionf(), 1.0f);
        t.scale(2f);
        assertEquals(2f, t.scale(), EPSILON);
        // Position remains (2, 4, 6) in a standard TRS implementation.
        assertEquals(2.0, t.position().x, EPSILON_D);
    }

    @Test
    void scaleAround_pivotIsStationary() {
        // Scaling (3,0,0) by factor 2 around pivot (1,0,0):
        // Offset is 2, scaled offset is 4, result is 1 + 4 = 5.
        Transform t = new Transform(new Vector3d(3, 0, 0), new Quaternionf(), 1.0f);
        Transform dest = new Transform();
        t.scaleAround(2f, 1, 0, 0, dest); // Assuming your new signature uses one float for scale

        assertEquals(5.0, dest.position().x, EPSILON_D);
        assertEquals(2.0f, dest.scale(), EPSILON);
    }

    // -------------------------------------------------------------------------
    // Composition (mul / premul)
    // -------------------------------------------------------------------------

    @Test
    void mul_positionTransformedByParentRotationAndScale() {
        // Parent: 90° Y rotation, scale 2, position (0,0,0).
        // Child: position (1,0,0), identity rotation, scale 1.
        // Expected child world position: parent.rot * (parent.scale * (1,0,0)) + parent.pos
        //   = rotY90 * (2,0,0) = (0,0,-2)
        Quaternionf rot = rotY90();
        Transform parent = new Transform(new Vector3d(0, 0, 0), rot, 2.0f);
        Transform child = new Transform(new Vector3d(1, 0, 0), new Quaternionf(), 1.0f);
        Transform result = new Transform();
        parent.mul(child, result);

        assertEquals(0.0, result.position().x, EPSILON);
        assertEquals(0.0, result.position().y, EPSILON);
        assertEquals(-2.0, result.position().z, EPSILON);
    }

    @Test
    void mul_scaleIsMultiplied() {
        Transform parent = new Transform(new Vector3d(), new Quaternionf(), 2.0f);
        Transform child = new Transform(new Vector3d(), new Quaternionf(), 5.0f);
        Transform result = new Transform();
        parent.mul(child, result);

        assertEquals(10.0f, result.scale(), EPSILON);
    }

    // -------------------------------------------------------------------------
    // Inversion
    // -------------------------------------------------------------------------

    @Test
    void invert_scaleIsReciprocal() {
        Transform t = new Transform(new Vector3d(), new Quaternionf(), 4.0f);
        Transform inv = new Transform();
        t.invert(inv);
        assertEquals(0.25f, inv.scale(), EPSILON);
    }

    @Test
    void invert_mulWithOriginal_isIdentity() {
        Transform t = new Transform(new Vector3d(3, -1, 7), rotY90(), 2.0f);
        Transform inv = new Transform();
        t.invert(inv);

        Transform result = new Transform();
        t.mul(inv, result);

        assertTransformEquals(new Transform(), result);
    }
}