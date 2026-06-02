package com.igrium.replaylab.test;

import com.igrium.replaylab.math.DynamicRotation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DynamicRotation}.
 *
 * <p>Storage convention used by this class (and JOML):
 * regardless of rotation order, angles are always stored as
 * {@code (Rx, Ry, Rz)} in the {@code (x, y, z)} components of a Vector3f.
 * Scalar setters accept parameters in rotation-application order
 * (e.g. {@code setEulerZYX(angleZ, angleY, angleX)}).
 */
class DynamicRotationTest {

    /** Acceptable floating-point tolerance for angle comparisons. */
    private static final float DELTA = 1e-5f;

    private DynamicRotation holder;

    @BeforeEach
    void setUp() {
        holder = new DynamicRotation();
    }

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    private void assertQuatEquals(Quaternionf expected, Quaternionf actual) {
        assertEquals(expected.x, actual.x, DELTA, "quaternion.x");
        assertEquals(expected.y, actual.y, DELTA, "quaternion.y");
        assertEquals(expected.z, actual.z, DELTA, "quaternion.z");
        assertEquals(expected.w, actual.w, DELTA, "quaternion.w");
    }

    private void assertVec3Equals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, DELTA, "vec.x (Rx)");
        assertEquals(expected.y, actual.y, DELTA, "vec.y (Ry)");
        assertEquals(expected.z, actual.z, DELTA, "vec.z (Rz)");
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    void defaultModeIsQuaternion() {
        assertEquals(DynamicRotation.RotationMode.QUATERNION, holder.mode());
    }

    @Test
    void defaultRotationIsIdentity() {
        Quaternionf result = new Quaternionf();
        holder.getQuaternion(result);
        assertQuatEquals(new Quaternionf(), result);
    }

    // -------------------------------------------------------------------------
    // QUATERNION mode
    // -------------------------------------------------------------------------

    @Nested
    class QuaternionMode {

        @Test
        void setAndGetQuaternion_roundTrip() {
            Quaternionf q = new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f);
            holder.setQuaternion(q);
            Quaternionf result = new Quaternionf();
            holder.getQuaternion(result);
            assertQuatEquals(q, result);
        }

        @Test
        void setQuaternion_returnsSelf() {
            assertSame(holder, holder.setQuaternion(new Quaternionf()));
        }
    }

    // -------------------------------------------------------------------------
    // EULER_XYZ mode
    // -------------------------------------------------------------------------

    @Nested
    class EulerXYZMode {

        @BeforeEach
        void enterMode() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
        }

        @Test
        void setAndGetEulerXYZ_roundTrip_scalar() {
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);
            Vector3f result = new Vector3f();
            holder.getEulerXYZ(result);
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        @Test
        void setAndGetEulerXYZ_roundTrip_vector() {
            Vector3f angles = new Vector3f(0.5f, 0.3f, 0.1f);
            holder.setEulerXYZ(angles);
            Vector3f result = new Vector3f();
            holder.getEulerXYZ(result);
            assertVec3Equals(angles, result);
        }

        @Test
        void getQuaternion_matchesJomlRotateXYZ() {
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);
            Quaternionf expected = new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f);
            Quaternionf result = new Quaternionf();
            holder.getQuaternion(result);
            assertQuatEquals(expected, result);
        }

        @Test
        void setEulerXYZ_returnsSelf() {
            assertSame(holder, holder.setEulerXYZ(0.1f, 0.2f, 0.3f));
        }

        @Test
        void setEulerXYZ_vectorOverload_returnsSelf() {
            assertSame(holder, holder.setEulerXYZ(new Vector3f(0.1f, 0.2f, 0.3f)));
        }
    }

    // -------------------------------------------------------------------------
    // EULER_ZYX mode
    // -------------------------------------------------------------------------

    @Nested
    class EulerZYXMode {

        @BeforeEach
        void enterMode() {
            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX);
        }

        /**
         * The scalar API takes parameters in rotation-application order: (angleZ, angleY, angleX).
         * The getter returns a Vector3f where x=Rx, y=Ry, z=Rz (JOML convention).
         */
        @Test
        void setAndGetEulerZYX_roundTrip_scalar() {
            // Rz=0.1, Ry=0.3, Rx=0.5
            holder.setEulerZYX(0.1f, 0.3f, 0.5f);
            Vector3f result = new Vector3f();
            holder.getEulerZYX(result);
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        /**
         * The vector API uses JOML convention throughout: (x=Rx, y=Ry, z=Rz).
         */
        @Test
        void setAndGetEulerZYX_roundTrip_vector() {
            Vector3f angles = new Vector3f(0.5f, 0.3f, 0.1f); // x=Rx, y=Ry, z=Rz
            holder.setEulerZYX(angles);
            Vector3f result = new Vector3f();
            holder.getEulerZYX(result);
            assertVec3Equals(angles, result);
        }

        @Test
        void getQuaternion_matchesJomlRotateZYX() {
            // setEulerZYX params: (angleZ, angleY, angleX)
            holder.setEulerZYX(0.1f, 0.3f, 0.5f);
            Quaternionf expected = new Quaternionf().rotateZYX(0.1f, 0.3f, 0.5f);
            Quaternionf result = new Quaternionf();
            holder.getQuaternion(result);
            assertQuatEquals(expected, result);
        }

        @Test
        void setEulerZYX_returnsSelf() {
            assertSame(holder, holder.setEulerZYX(0.1f, 0.2f, 0.3f));
        }

        @Test
        void setEulerZYX_vectorOverload_returnsSelf() {
            assertSame(holder, holder.setEulerZYX(new Vector3f(0.1f, 0.2f, 0.3f)));
        }
    }

    // -------------------------------------------------------------------------
    // EULER_YXZ mode
    // -------------------------------------------------------------------------

    @Nested
    class EulerYXZMode {

        @BeforeEach
        void enterMode() {
            holder.setMode(DynamicRotation.RotationMode.EULER_YXZ);
        }

        /**
         * The scalar API takes parameters in rotation-application order: (angleY, angleX, angleZ).
         * The getter returns a Vector3f where x=Rx, y=Ry, z=Rz.
         */
        @Test
        void setAndGetEulerYXZ_roundTrip_scalar() {
            // Ry=0.3, Rx=0.5, Rz=0.1
            holder.setEulerYXZ(0.3f, 0.5f, 0.1f);
            Vector3f result = new Vector3f();
            holder.getEulerYXZ(result);
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        @Test
        void setAndGetEulerYXZ_roundTrip_vector() {
            Vector3f angles = new Vector3f(0.5f, 0.3f, 0.1f); // x=Rx, y=Ry, z=Rz
            holder.setEulerYXZ(angles);
            Vector3f result = new Vector3f();
            holder.getEulerYXZ(result);
            assertVec3Equals(angles, result);
        }

        @Test
        void getQuaternion_matchesJomlRotateYXZ() {
            // setEulerYXZ params: (angleY, angleX, angleZ)
            holder.setEulerYXZ(0.3f, 0.5f, 0.1f);
            Quaternionf expected = new Quaternionf().rotateYXZ(0.3f, 0.5f, 0.1f);
            Quaternionf result = new Quaternionf();
            holder.getQuaternion(result);
            assertQuatEquals(expected, result);
        }

        @Test
        void setEulerYXZ_returnsSelf() {
            assertSame(holder, holder.setEulerYXZ(0.1f, 0.2f, 0.3f));
        }

        @Test
        void setEulerYXZ_vectorOverload_returnsSelf() {
            assertSame(holder, holder.setEulerYXZ(new Vector3f(0.1f, 0.2f, 0.3f)));
        }
    }

    // -------------------------------------------------------------------------
    // Cross-mode: getter in a mode different from the stored mode
    // -------------------------------------------------------------------------

    @Nested
    class CrossModeGetters {

        @Test
        void getEulerXYZ_fromQuaternion() {
            holder.setQuaternion(new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f));
            Vector3f result = new Vector3f();
            holder.getEulerXYZ(result);
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        @Test
        void getEulerZYX_fromQuaternion() {
            // JOML rotateZYX takes (angleZ, angleY, angleX)
            holder.setQuaternion(new Quaternionf().rotateZYX(0.1f, 0.3f, 0.5f));
            Vector3f result = new Vector3f();
            holder.getEulerZYX(result);
            // Expected: x=Rx=0.5, y=Ry=0.3, z=Rz=0.1
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        @Test
        void getEulerYXZ_fromQuaternion() {
            // JOML rotateYXZ takes (angleY, angleX, angleZ)
            holder.setQuaternion(new Quaternionf().rotateYXZ(0.3f, 0.5f, 0.1f));
            Vector3f result = new Vector3f();
            holder.getEulerYXZ(result);
            // Expected: x=Rx=0.5, y=Ry=0.3, z=Rz=0.1
            assertEquals(0.5f, result.x, DELTA, "Rx");
            assertEquals(0.3f, result.y, DELTA, "Ry");
            assertEquals(0.1f, result.z, DELTA, "Rz");
        }

        @Test
        void getEulerXYZ_fromEulerZYX_goesViaQuaternion() {
            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX);
            holder.setEulerZYX(0.1f, 0.3f, 0.5f); // Rz=0.1, Ry=0.3, Rx=0.5

            // Ground-truth: what JOML says the XYZ decomposition is
            Vector3f expected = new Quaternionf()
                    .rotateZYX(0.1f, 0.3f, 0.5f)
                    .getEulerAnglesXYZ(new Vector3f());

            Vector3f result = new Vector3f();
            holder.getEulerXYZ(result);
            assertVec3Equals(expected, result);
        }

        @Test
        void getEulerZYX_fromEulerXYZ_goesViaQuaternion() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);

            Vector3f expected = new Quaternionf()
                    .rotateXYZ(0.5f, 0.3f, 0.1f)
                    .getEulerAnglesZYX(new Vector3f());

            Vector3f result = new Vector3f();
            holder.getEulerZYX(result);
            assertVec3Equals(expected, result);
        }

        @Test
        void getEulerYXZ_fromEulerXYZ_goesViaQuaternion() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);

            Vector3f expected = new Quaternionf()
                    .rotateXYZ(0.5f, 0.3f, 0.1f)
                    .getEulerAnglesYXZ(new Vector3f());

            Vector3f result = new Vector3f();
            holder.getEulerYXZ(result);
            assertVec3Equals(expected, result);
        }
    }

    // -------------------------------------------------------------------------
    // setMode — mode switching
    // -------------------------------------------------------------------------

    @Nested
    class SetModeTests {

        @Test
        void setMode_toSameMode_isNoOp_withQuaternion() {
            holder.setQuaternion(new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f));
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.QUATERNION); // already QUATERNION
            assertEquals(DynamicRotation.RotationMode.QUATERNION, holder.mode());
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_toSameMode_isNoOp_withEuler() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ); // same
            assertEquals(DynamicRotation.RotationMode.EULER_XYZ, holder.mode());
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        // -- convert = true (default) -----------------------------------------

        @Test
        void setMode_withConversion_quaternionToEulerXYZ_preservesRotation() {
            holder.setQuaternion(new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f));
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ, true);
            assertEquals(DynamicRotation.RotationMode.EULER_XYZ, holder.mode());
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_withConversion_quaternionToEulerZYX_preservesRotation() {
            holder.setQuaternion(new Quaternionf().rotateZYX(0.1f, 0.3f, 0.5f));
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX, true);
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_withConversion_quaternionToEulerYXZ_preservesRotation() {
            holder.setQuaternion(new Quaternionf().rotateYXZ(0.3f, 0.5f, 0.1f));
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_YXZ, true);
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_withConversion_eulerXYZ_toEulerZYX_preservesRotation() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX, true);
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_withConversion_eulerZYX_toEulerYXZ_preservesRotation() {
            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX);
            holder.setEulerZYX(0.1f, 0.3f, 0.5f);
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_YXZ, true);
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        @Test
        void setMode_withConversion_eulerBack_toQuaternion_preservesRotation() {
            holder.setMode(DynamicRotation.RotationMode.EULER_YXZ);
            holder.setEulerYXZ(0.3f, 0.5f, 0.1f);
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.QUATERNION, true);
            assertEquals(DynamicRotation.RotationMode.QUATERNION, holder.mode());
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        /**
         * The single-argument overload must delegate to {@code setMode(mode, true)}.
         */
        @Test
        void setMode_noConvertArg_defaultsToConvertTrue() {
            holder.setQuaternion(new Quaternionf().rotateXYZ(0.5f, 0.3f, 0.1f));
            Quaternionf before = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX); // implicit convert=true
            assertQuatEquals(before, holder.getQuaternion(new Quaternionf()));
        }

        // -- convert = false --------------------------------------------------

        /**
         * With {@code convert=false} only the mode label changes; the raw stored
         * values are reinterpreted under the new rotation order, producing a
         * different effective rotation for non-zero, non-trivial angles.
         */
        @Test
        void setMode_withoutConversion_onlyChangesMode() {
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0.5f, 0.3f, 0.1f);
            Quaternionf xyzQuat = holder.getQuaternion(new Quaternionf());

            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX, false);
            assertEquals(DynamicRotation.RotationMode.EULER_ZYX, holder.mode(),
                    "Mode label should have changed");

            // The same raw euler data (0.5, 0.3, 0.1) is now fed into rotateZYX,
            // which is a different rotation from rotateXYZ.
            Quaternionf reinterpretedQuat = holder.getQuaternion(new Quaternionf());
            boolean sameAsXYZ =
                    Math.abs(xyzQuat.x - reinterpretedQuat.x) < DELTA &&
                            Math.abs(xyzQuat.y - reinterpretedQuat.y) < DELTA &&
                            Math.abs(xyzQuat.z - reinterpretedQuat.z) < DELTA &&
                            Math.abs(xyzQuat.w - reinterpretedQuat.w) < DELTA;
            assertFalse(sameAsXYZ,
                    "Reinterpreted rotation should differ from original for non-trivial angles");
        }

        @Test
        void setMode_withoutConversion_identityRotation_remainsIdentity() {
            // Identity (all-zero euler) is the same regardless of rotation order.
            holder.setMode(DynamicRotation.RotationMode.EULER_XYZ);
            holder.setEulerXYZ(0f, 0f, 0f);

            holder.setMode(DynamicRotation.RotationMode.EULER_ZYX, false);

            Quaternionf result = holder.getQuaternion(new Quaternionf());
            assertQuatEquals(new Quaternionf(), result);
        }
    }
}