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

    // -------------------------------------------------------------------------
// toMatrix / fromMatrix
// -------------------------------------------------------------------------

    /** Extracts the translation from a Matrix4f into a Vector3f. */
    private static Vector3f matrixTranslation(Matrix4f m) {
        return new Vector3f(m.m30(), m.m31(), m.m32());
    }

    /** Extracts the uniform scale from a Matrix4f by measuring the first column's length. */
    private static float matrixColumnScale(Matrix4f m) {
        return (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
    }

// --- toMatrix ---

    @Test
    void toMatrix_identity_producesIdentityMatrix() {
        Transform t = new Transform();
        Matrix4f result = t.toMatrix(new Matrix4f());
        assertTrue(result.equals(new Matrix4f(), EPSILON), "Identity transform should produce identity matrix");
    }

    @Test
    void toMatrix_translationOnly_setsMatrixTranslation() {
        Transform t = new Transform(new Vector3d(3, 7, -2), new Quaternionf(), 1f);
        Matrix4f result = t.toMatrix(new Matrix4f());
        Vector3f translation = matrixTranslation(result);
        assertEquals(3f, translation.x, EPSILON);
        assertEquals(7f, translation.y, EPSILON);
        assertEquals(-2f, translation.z, EPSILON);
    }

    @Test
    void toMatrix_withRoot_subtractsRootFromTranslation() {
        // Object at world (1000.5, 0, 0) with root (1000, 0, 0) → matrix should encode (0.5, 0, 0)
        Transform t = new Transform(new Vector3d(1000.5, 0, 0), new Quaternionf(), 1f);
        Matrix4f result = t.toMatrix(1000, 0, 0, new Matrix4f());
        Vector3f translation = matrixTranslation(result);
        assertEquals(0.5f, translation.x, EPSILON);
        assertEquals(0f, translation.y, EPSILON);
        assertEquals(0f, translation.z, EPSILON);
    }

    @Test
    void toMatrix_withVectorRoot_matchesPrimitiveRoot() {
        Transform t = new Transform(new Vector3d(5, 10, 15), new Quaternionf(), 1f);
        Matrix4f viaVector = t.toMatrix(new Vector3d(1, 2, 3), new Matrix4f());
        Matrix4f viaPrimitive = t.toMatrix(1, 2, 3, new Matrix4f());
        assertTrue(viaVector.equals(viaPrimitive, EPSILON));
    }

    @Test
    void toMatrix_zeroRoot_equivalentToNoRootOverload() {
        Transform t = new Transform(new Vector3d(3, 1, 4), rotY90(), 2f);
        Matrix4f withZeroRoot = t.toMatrix(0, 0, 0, new Matrix4f());
        Matrix4f noRoot = t.toMatrix(new Matrix4f());
        assertTrue(withZeroRoot.equals(noRoot, EPSILON));
    }

    @Test
    void toMatrix_uniformScale_scalesMatrixColumns() {
        Transform t = new Transform(new Vector3d(), new Quaternionf(), 3f);
        Matrix4f result = t.toMatrix(new Matrix4f());
        // Each basis column should have length 3.
        assertEquals(3f, matrixColumnScale(result), EPSILON);
    }

    @Test
    void toMatrix_rotation90Y_transformsBasisCorrectly() {
        // A 90° Y rotation maps +X to -Z and +Z to +X.
        Transform t = new Transform(new Vector3d(), rotY90(), 1f);
        Matrix4f result = t.toMatrix(new Matrix4f());

        // The X column of the matrix is where (1,0,0) lands.
        assertEquals(0f,  result.m00(), EPSILON); // x → 0 in X
        assertEquals(0f,  result.m01(), EPSILON); // x → 0 in Y
        assertEquals(-1f, result.m02(), EPSILON); // x → -1 in Z
    }

    @Test
    void toMatrix_accumulatesOnTopOfExistingDest() {
        // Start dest with a translation of (1,0,0), then apply transform with (0,0,2).
        // Expected: net translation (1,0,2) — dest is NOT reset to identity.
        Matrix4f dest = new Matrix4f().translate(1, 0, 0);
        Transform t = new Transform(new Vector3d(0, 0, 2), new Quaternionf(), 1f);
        t.toMatrix(dest);
        Vector3f translation = matrixTranslation(dest);
        assertEquals(1f, translation.x, EPSILON);
        assertEquals(0f, translation.y, EPSILON);
        assertEquals(2f, translation.z, EPSILON);
    }

    @Test
    void toMatrix_returnsDest() {
        Transform t = new Transform();
        Matrix4f dest = new Matrix4f();
        assertSame(dest, t.toMatrix(dest));
        assertSame(dest, t.toMatrix(0, 0, 0, dest));
        assertSame(dest, t.toMatrix(new Vector3d(), dest));
    }

// --- fromMatrix ---

    @Test
    void fromMatrix_identityMatrix_producesIdentityTransform() {
        Transform t = new Transform(new Vector3d(5, 5, 5), rotY90(), 3f);
        t.fromMatrix(new Matrix4f());
        assertTransformEquals(new Transform(), t);
    }

    @Test
    void fromMatrix_extractsTranslation() {
        Matrix4f m = new Matrix4f().translate(4, -2, 7);
        Transform t = new Transform();
        t.fromMatrix(m);
        assertEquals(4.0, t.position().x, EPSILON_D);
        assertEquals(-2.0, t.position().y, EPSILON_D);
        assertEquals(7.0, t.position().z, EPSILON_D);
    }

    @Test
    void fromMatrix_extractsUniformScale() {
        Matrix4f m = new Matrix4f().scale(5f);
        Transform t = new Transform();
        t.fromMatrix(m);
        assertEquals(5f, t.scale(), EPSILON);
    }

    @Test
    void fromMatrix_extractsRotation() {
        Quaternionf rot = rotY90();
        Matrix4f m = new Matrix4f().rotate(rot);
        Transform t = new Transform();
        t.fromMatrix(m);
        float dot = Math.abs(rot.dot(t.rotation()));
        assertTrue(dot > 1f - EPSILON, "Extracted rotation should match the matrix rotation");
    }

    @Test
    void fromMatrix_returnsThis() {
        Transform t = new Transform();
        assertSame(t, t.fromMatrix(new Matrix4f()));
    }

// --- round-trip ---

    @Test
    void roundTrip_toMatrixThenFromMatrix_restoresTransform() {
        Transform original = new Transform(new Vector3d(3, -1, 2), rotY90(), 2f);

        Matrix4f matrix = original.toMatrix(new Matrix4f());
        Transform restored = new Transform();
        restored.fromMatrix(matrix);

        assertEquals(original.position().x, restored.position().x, EPSILON);
        assertEquals(original.position().y, restored.position().y, EPSILON);
        assertEquals(original.position().z, restored.position().z, EPSILON);
        assertEquals(original.scale(), restored.scale(), EPSILON);
        float dot = Math.abs(original.rotation().dot(restored.rotation()));
        assertTrue(dot > 1f - EPSILON, "Rotation should survive the round-trip");
    }

    @Test
    void roundTrip_withRotationAndScale_preservesOrientation() {
        // 45° X rotation with scale 3 — a combined TRS that stresses all three components.
        Quaternionf rot = new Quaternionf().rotateX((float) (Math.PI / 4));
        Transform original = new Transform(new Vector3d(10, 20, 30), rot, 3f);

        Transform restored = new Transform();
        restored.fromMatrix(original.toMatrix(new Matrix4f()));

        float dot = Math.abs(original.rotation().dot(restored.rotation()));
        assertTrue(dot > 1f - EPSILON);
        assertEquals(original.scale(), restored.scale(), EPSILON);
    }

// --- getUniformScale precision ---

    @Test
    void getUniformScale_uniformScale_isExact() {
        // With a true uniform scale, the averaged column magnitudes must equal the scale exactly.
        for (float s : new float[]{ 0.5f, 1f, 2f, 10f }) {
            Transform t = new Transform(new Vector3d(), rotY90(), s);
            Matrix4f m = t.toMatrix(new Matrix4f());
            Transform restored = new Transform();
            restored.fromMatrix(m);
            assertEquals(s, restored.scale(), EPSILON,
                    "getUniformScale should be exact for uniform scale " + s);
        }
    }

    @Test
    void toMatrix_largeCoordinates_rootReducesPrecisionLoss() {
        // At coordinates ~1e7 the float precision is ~1 unit; the root offset should
        // reduce the stored value to ~0.5 which is representable precisely in float.
        double worldX = 1_000_000.5;
        Transform t = new Transform(new Vector3d(worldX, 0, 0), new Quaternionf(), 1f);

        Matrix4f withRoot    = t.toMatrix(1_000_000, 0, 0, new Matrix4f());
        Matrix4f withoutRoot = t.toMatrix(new Matrix4f());

        float offsetWithRoot    = matrixTranslation(withRoot).x;
        float offsetWithoutRoot = matrixTranslation(withoutRoot).x;

        // The root-relative value (0.5f) should be much closer to the true fractional offset.
        float trueOffset = (float)(worldX - 1_000_000);
        assertTrue(Math.abs(offsetWithRoot - trueOffset) < Math.abs(offsetWithoutRoot - trueOffset),
                "Root-relative encoding should be more precise than absolute encoding");
    }
}