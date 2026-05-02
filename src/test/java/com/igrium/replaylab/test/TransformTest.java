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
    private static void assertTransformEquals(Transform expected, Transform actual, float eps) {
        assertEquals(expected.position().x, actual.position().x, eps, "position.x");
        assertEquals(expected.position().y, actual.position().y, eps, "position.y");
        assertEquals(expected.position().z, actual.position().z, eps, "position.z");

        // Quaternions q and -q represent the same rotation; normalise sign before comparing.
        float dot = expected.rotation().dot(actual.rotation());
        assertTrue(Math.abs(dot) > 1f - eps,
                () -> "rotation mismatch: expected " + expected.rotation() + " but got " + actual.rotation());

        assertEquals(expected.scale().x, actual.scale().x, eps, "scale.x");
        assertEquals(expected.scale().y, actual.scale().y, eps, "scale.y");
        assertEquals(expected.scale().z, actual.scale().z, eps, "scale.z");
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
        assertEquals(1f, t.scale().x, EPSILON);
        assertEquals(1f, t.scale().y, EPSILON);
        assertEquals(1f, t.scale().z, EPSILON);
    }

    @Test
    void fullConstructor_copiesComponents() {
        Vector3d pos = new Vector3d(1, 2, 3);
        Quaternionf rot = rotY90();
        Vector3f scale = new Vector3f(2, 3, 4);

        Transform t = new Transform(pos, rot, scale);

        assertEquals(pos.x, t.position().x, EPSILON_D);
        assertEquals(pos.y, t.position().y, EPSILON_D);
        assertEquals(pos.z, t.position().z, EPSILON_D);
        assertTrue(t.rotation().equals(rot, EPSILON));
        assertEquals(scale.x, t.scale().x, EPSILON);
        assertEquals(scale.y, t.scale().y, EPSILON);
        assertEquals(scale.z, t.scale().z, EPSILON);
    }

    @Test
    void uniformScaleConstructor_setsAllAxes() {
        Transform t = new Transform(new Vector3d(), new Quaternionf(), 3f);
        assertEquals(3f, t.scale().x, EPSILON);
        assertEquals(3f, t.scale().y, EPSILON);
        assertEquals(3f, t.scale().z, EPSILON);
    }

    @Test
    void constructor_doesNotRetainInputReferences() {
        Vector3d pos = new Vector3d(1, 2, 3);
        Quaternionf rot = new Quaternionf();
        Vector3f scale = new Vector3f(1, 1, 1);
        Transform t = new Transform(pos, rot, scale);

        pos.set(99, 99, 99);
        rot.set(1, 0, 0, 0);
        scale.set(0, 0, 0);

        assertEquals(1.0, t.position().x, EPSILON_D, "position must be copied, not aliased");
        assertTrue(t.rotation().equals(new Quaternionf(), EPSILON), "rotation must be copied");
        assertEquals(1f, t.scale().x, EPSILON, "scale must be copied");
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------

    @Test
    void translate_primitives_offsetsPosition() {
        Transform t = new Transform(new Vector3d(1, 2, 3), new Quaternionf(), new Vector3f(1, 1, 1));
        t.translate(10, 20, 30);
        assertEquals(11.0, t.position().x, EPSILON_D);
        assertEquals(22.0, t.position().y, EPSILON_D);
        assertEquals(33.0, t.position().z, EPSILON_D);
    }

    @Test
    void translate_doesNotAffectRotationOrScale() {
        Quaternionf rot = rotY90();
        Transform t = new Transform(new Vector3d(), rot, new Vector3f(2, 3, 4));
        t.translate(5, 0, 0);
        assertTrue(t.rotation().equals(rot, EPSILON));
        assertEquals(2f, t.scale().x, EPSILON);
    }

    @Test
    void translate_withDest_doesNotMutateSource() {
        Transform src = new Transform(new Vector3d(1, 1, 1), new Quaternionf(), new Vector3f(1, 1, 1));
        Transform dest = new Transform();
        src.translate(5, 5, 5, dest);

        assertEquals(1.0, src.position().x, EPSILON_D, "source must not be mutated");
        assertEquals(6.0, dest.position().x, EPSILON_D);
    }

    @Test
    void translate_inPlace_returnsThis() {
        Transform t = new Transform();
        assertSame(t, t.translate(1, 2, 3));
    }

    // -------------------------------------------------------------------------
    // Rotation
    // -------------------------------------------------------------------------
//
//    @Test
//    void rotate_isLocalSpace() {
//        // Local-space means: this.rotation = this.rotation * newRot
//        Quaternionf initial = rotY90();
//        Quaternionf additional = new Quaternionf().rotateX((float) (Math.PI / 2));
//        Transform t = new Transform(new Vector3d(3, -1, 7), rotY90(), new Vector3f(2, 2, 2));
//        t.rotate(additional);
//
//        Quaternionf expected = new Quaternionf(initial).mul(additional);
//        assertTrue(t.rotation().equals(expected, EPSILON));
//    }

    @Test
    void rotate_doesNotAffectPositionOrScale() {
        Transform t = new Transform(new Vector3d(5, 10, 15), new Quaternionf(), new Vector3f(2, 3, 4));
        t.rotate(rotY90());
        assertEquals(5.0, t.position().x, EPSILON_D);
        assertEquals(10.0, t.position().y, EPSILON_D);
        assertEquals(15.0, t.position().z, EPSILON_D);
        assertEquals(2f, t.scale().x, EPSILON);
    }

    @Test
    void rotate_inPlace_returnsThis() {
        Transform t = new Transform();
        assertSame(t, t.rotate(new Quaternionf()));
    }

    @Test
    void rotateAround_pivotIsStationary() {
        // A 90° Y-rotation around the pivot (1,0,0) should move (2,0,0) to (1,0,-1).
        Transform t = new Transform(new Vector3d(2, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));
        Quaternionf rot = rotY90();
        Transform dest = new Transform();
        t.rotateAround(rot, 1, 0, 0, dest);

        assertEquals(1.0, dest.position().x, EPSILON);
        assertEquals(0.0, dest.position().y, EPSILON);
        assertEquals(-1.0, dest.position().z, EPSILON);
    }

    @Test
    void rotateAround_pivotAtOrigin_equivalentToNormalRotate() {
        // When the pivot is the origin, rotateAround == rotate for position too.
        Transform t = new Transform(new Vector3d(1, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));
        Quaternionf rot = rotY90();

        Transform viaAround = new Transform();
        Transform viaMul = new Transform();
        t.rotateAround(rot, 0, 0, 0, viaAround);

        // Expected: position rotated by rot
        Vector3d expectedPos = new Vector3d(1, 0, 0);
        rot.transform(new Vector3f((float) expectedPos.x, (float) expectedPos.y, (float) expectedPos.z))
                .get(viaMul.position());

        assertEquals(viaMul.position().x, viaAround.position().x, EPSILON);
        assertEquals(viaMul.position().y, viaAround.position().y, EPSILON);
        assertEquals(viaMul.position().z, viaAround.position().z, EPSILON);
    }

    // -------------------------------------------------------------------------
    // Scale
    // -------------------------------------------------------------------------

    @Test
    void scale_uniform_multipliesPositionAndScale() {
        Transform t = new Transform(new Vector3d(2, 4, 6), new Quaternionf(), new Vector3f(1, 2, 3));
        t.scale(2f);
        assertEquals(4.0, t.position().x, EPSILON_D);
        assertEquals(8.0, t.position().y, EPSILON_D);
        assertEquals(12.0, t.position().z, EPSILON_D);
        assertEquals(2f, t.scale().x, EPSILON);
        assertEquals(4f, t.scale().y, EPSILON);
        assertEquals(6f, t.scale().z, EPSILON);
    }

    @Test
    void scale_doesNotAffectRotation() {
        Quaternionf rot = rotY90();
        Transform t = new Transform(new Vector3d(), rot, new Vector3f(1, 1, 1));
        t.scale(3f);
        assertTrue(t.rotation().equals(rot, EPSILON));
    }

    @Test
    void scaleAround_pivotIsStationary() {
        // Scaling (2,0,0) by 2 around pivot (0,0,0): result is (4,0,0).
        // Scaling (3,0,0) by 2 around pivot (1,0,0): offset=(2,0,0), scaled=(4,0,0), + pivot=(5,0,0).
        Transform t = new Transform(new Vector3d(3, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));
        Transform dest = new Transform();
        t.scaleAround(2f, 2f, 2f, 1, 0, 0, dest);

        assertEquals(5.0, dest.position().x, EPSILON_D);
        assertEquals(0.0, dest.position().y, EPSILON_D);
        assertEquals(0.0, dest.position().z, EPSILON_D);
        assertEquals(2f, dest.scale().x, EPSILON);
    }

    @Test
    void scaleAround_pivotAtOrigin_equivalentToScale() {
        Transform a = new Transform(new Vector3d(3, 1, 2), new Quaternionf(), new Vector3f(1, 1, 1));
        Transform b = new Transform(new Vector3d(3, 1, 2), new Quaternionf(), new Vector3f(1, 1, 1));
        a.scale(2f);
        b.scaleAround(2f, 2f, 2f, 0, 0, 0);
        assertTransformEquals(a, b, EPSILON);
    }

    // -------------------------------------------------------------------------
    // Composition (mul / premul)
    // -------------------------------------------------------------------------

    @Test
    void mul_identity_isNoOp() {
        Transform parent = new Transform(new Vector3d(5, 0, 0), rotY90(), new Vector3f(2, 2, 2));
        Transform identity = new Transform();
        Transform result = new Transform();
        parent.mul(identity, result);
        assertTransformEquals(parent, result, EPSILON);
    }

    @Test
    void mul_positionTransformedByParentRotationAndScale() {
        // Parent: 90° Y rotation, uniform scale 2, position (0,0,0).
        // Child: position (1,0,0), identity rotation, unit scale.
        // Expected child world position: parent.rot * (parent.scale * (1,0,0)) + parent.pos
        //   = rotY90 * (2,0,0) = (0,0,-2)
        Quaternionf rot = rotY90();
        Transform parent = new Transform(new Vector3d(0, 0, 0), rot, new Vector3f(2, 2, 2));
        Transform child = new Transform(new Vector3d(1, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));
        Transform result = new Transform();
        parent.mul(child, result);

        assertEquals(0.0, result.position().x, EPSILON);
        assertEquals(0.0, result.position().y, EPSILON);
        assertEquals(-2.0, result.position().z, EPSILON);
    }

    @Test
    void mul_rotationIsCombined() {
        Quaternionf parentRot = rotY90();
        Quaternionf childRot = new Quaternionf().rotateX((float) (Math.PI / 2));
        Transform parent = new Transform(new Vector3d(), parentRot, new Vector3f(1, 1, 1));
        Transform child = new Transform(new Vector3d(), childRot, new Vector3f(1, 1, 1));
        Transform result = new Transform();
        parent.mul(child, result);

        Quaternionf expected = new Quaternionf(parentRot).mul(childRot);
        assertTrue(result.rotation().equals(expected, EPSILON));
    }

    @Test
    void mul_scaleIsComponentWise() {
        Transform parent = new Transform(new Vector3d(), new Quaternionf(), new Vector3f(2, 3, 4));
        Transform child = new Transform(new Vector3d(), new Quaternionf(), new Vector3f(5, 6, 7));
        Transform result = new Transform();
        parent.mul(child, result);

        assertEquals(10f, result.scale().x, EPSILON);
        assertEquals(18f, result.scale().y, EPSILON);
        assertEquals(28f, result.scale().z, EPSILON);
    }

    @Test
    void mul_inPlace_equivalentToDestThis() {
        Transform parent = new Transform(new Vector3d(1, 2, 3), rotY90(), new Vector3f(2, 1, 1));
        Transform child = new Transform(new Vector3d(4, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));

        Transform expected = new Transform();
        new Transform(parent.position(), parent.rotation(), parent.scale()).mul(child, expected);

        parent.mul(child); // in-place
        assertTransformEquals(expected, parent, EPSILON);
    }

    @Test
    void mul_aliasSafe_destEqualsChild() {
        Transform parent = new Transform(new Vector3d(1, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));
        Transform child = new Transform(new Vector3d(0, 1, 0), new Quaternionf(), new Vector3f(1, 1, 1));

        Transform expected = new Transform();
        parent.mul(child, expected);

        parent.mul(child, child); // dest == child
        assertTransformEquals(expected, child, EPSILON);
    }

    @Test
    void mul_aliasSafe_destEqualsParent() {
        Transform parent = new Transform(new Vector3d(1, 0, 0), new Quaternionf(), new Vector3f(2, 2, 2));
        Transform child = new Transform(new Vector3d(3, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));

        Transform expected = new Transform();
        parent.mul(child, expected);

        parent.mul(child, parent); // dest == parent (same as in-place)
        assertTransformEquals(expected, parent, EPSILON);
    }

    @Test
    void premul_equivalentToParentMulThis() {
        Transform parent = new Transform(new Vector3d(1, 2, 3), rotY90(), new Vector3f(2, 1, 1));
        Transform child = new Transform(new Vector3d(4, 0, 0), new Quaternionf(), new Vector3f(1, 1, 1));

        Transform viaParentMul = new Transform();
        Transform viaPremul = new Transform();

        parent.mul(child, viaParentMul);
        child.premul(parent, viaPremul);

        assertTransformEquals(viaParentMul, viaPremul, EPSILON);
    }

    @Test
    void mul_isNotCommutative() {
        // TRS multiplication is generally non-commutative.
        Transform a = new Transform(new Vector3d(1, 0, 0), rotY90(), new Vector3f(1, 1, 1));
        Transform b = new Transform(new Vector3d(0, 0, 1), new Quaternionf(), new Vector3f(1, 1, 1));

        Transform ab = new Transform();
        Transform ba = new Transform();
        a.mul(b, ab);
        b.mul(a, ba);

        // Positions will differ; verify they are not equal.
        boolean positionsDiffer =
                Math.abs(ab.position().x - ba.position().x) > EPSILON ||
                        Math.abs(ab.position().y - ba.position().y) > EPSILON ||
                        Math.abs(ab.position().z - ba.position().z) > EPSILON;
        assertTrue(positionsDiffer, "TRS composition should be non-commutative in general");
    }

    // -------------------------------------------------------------------------
    // Inversion
    // -------------------------------------------------------------------------

    @Test
    void invert_mulWithOriginal_isIdentity() {
        Transform t = new Transform(new Vector3d(3, -1, 7), rotY90(), new Vector3f(2, 2, 2));
        Transform inv = new Transform();
        t.invert(inv);

        Transform result = new Transform();
        t.mul(inv, result);

        Transform identity = new Transform();
        assertTransformEquals(identity, result, EPSILON);
    }

    @Test
    void invert_inPlace_mulWithOriginal_isIdentity() {
        Transform original = new Transform(new Vector3d(1, 2, 3), rotY90(), new Vector3f(2, 2, 2));
        Transform copy = new Transform(original.position(), original.rotation(), original.scale());
        original.invert();

        Transform result = new Transform();
        copy.mul(original, result);

        assertTransformEquals(new Transform(), result, EPSILON);
    }

    @Test
    void invert_ofIdentity_isIdentity() {
        Transform t = new Transform();
        t.invert();
        assertTransformEquals(new Transform(), t, EPSILON);
    }

    @Test
    void invert_involutory_doubleInvertRestoresOriginal() {
        Transform original = new Transform(new Vector3d(5, -3, 2), rotY90(), new Vector3f(2, 2, 2));
        Transform copy = new Transform(original.position(), original.rotation(), original.scale());
        original.invert();
        original.invert();
        assertTransformEquals(copy, original, EPSILON);
    }

    @Test
    void invert_scaleIsReciprocal() {
        Transform t = new Transform(new Vector3d(), new Quaternionf(), new Vector3f(2, 4, 8));
        Transform inv = new Transform();
        t.invert(inv);
        assertEquals(0.5f, inv.scale().x, EPSILON);
        assertEquals(0.25f, inv.scale().y, EPSILON);
        assertEquals(0.125f, inv.scale().z, EPSILON);
    }

    @Test
    void invert_rotationIsConjugate() {
        Quaternionf rot = rotY90();
        Transform t = new Transform(new Vector3d(), rot, new Vector3f(1, 1, 1));
        Transform inv = new Transform();
        t.invert(inv);
        Quaternionf expected = new Quaternionf(rot).conjugate();
        assertTrue(inv.rotation().equals(expected, EPSILON));
    }
}