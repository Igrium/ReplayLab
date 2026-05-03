package com.igrium.replaylab.test;

import com.igrium.replaylab.math.Transform3;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vibe-coded unit tests. Bite me.
 */
class Transform3Test {
    private static final double D_EPS = 1e-6;
    private static final float F_EPS = 1e-3f;

    private static void assertVec3(Vector3dc v, double x, double y, double z) {
        String expected = new Vector3d(x, y, z).toString();
        String actual = v.toString();
        assertEquals(x, v.x(), D_EPS, "Expected " + expected + ", got " + actual);
        assertEquals(y, v.y(), D_EPS, "Expected " + expected + ", got " + actual);
        assertEquals(z, v.z(), D_EPS, "Expected " + expected + ", got " + actual);
    }

    private static void assertMatrix3(Matrix3fc m,
                                      float m00, float m01, float m02,
                                      float m10, float m11, float m12,
                                      float m20, float m21, float m22) {

        String expected = String.format(
                "[[%f, %f, %f], [%f, %f, %f], [%f, %f, %f]]",
                m00, m01, m02,
                m10, m11, m12,
                m20, m21, m22
        );

        String actual = String.format(
                "[[%f, %f, %f], [%f, %f, %f], [%f, %f, %f]]",
                m.m00(), m.m01(), m.m02(),
                m.m10(), m.m11(), m.m12(),
                m.m20(), m.m21(), m.m22()
        );

        assertAll(
                () -> assertEquals(m00, m.m00(), F_EPS, "m00 expected " + expected + " but was " + actual),
                () -> assertEquals(m01, m.m01(), F_EPS, "m01 expected " + expected + " but was " + actual),
                () -> assertEquals(m02, m.m02(), F_EPS, "m02 expected " + expected + " but was " + actual),
                () -> assertEquals(m10, m.m10(), F_EPS, "m10 expected " + expected + " but was " + actual),
                () -> assertEquals(m11, m.m11(), F_EPS, "m11 expected " + expected + " but was " + actual),
                () -> assertEquals(m12, m.m12(), F_EPS, "m12 expected " + expected + " but was " + actual),
                () -> assertEquals(m20, m.m20(), F_EPS, "m20 expected " + expected + " but was " + actual),
                () -> assertEquals(m21, m.m21(), F_EPS, "m21 expected " + expected + " but was " + actual),
                () -> assertEquals(m22, m.m22(), F_EPS, "m22 expected " + expected + " but was " + actual)
        );
    }

    @Test
    void defaultConstructorCreatesIdentityAtOrigin() {
        Transform3 t = new Transform3();

        assertVec3(t.pos(), 0.0, 0.0, 0.0);
        assertMatrix3(t.rotScale(),
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f);
    }

    @Test
    void copyConstructorCopiesValues() {
        Vector3d pos = new Vector3d(1.0, 2.0, 3.0);
        Matrix3f mat = new Matrix3f().rotationZ((float) Math.PI / 2.0f);

        Transform3 t = new Transform3((Vector3dc) pos, (Matrix3fc) mat);

        assertVec3(t.pos(), 1.0, 2.0, 3.0);
        assertMatrix3(t.rotScale(),
                0f,  1f, 0f,
                -1f,  0f, 0f,
                0f,  0f, 1f);
    }

    @Test
    void copyConstructorIsDeepCopyPosition() {
        Vector3d pos = new Vector3d(1.0, 2.0, 3.0);
        Matrix3f mat = new Matrix3f().rotationZ((float) Math.PI / 2.0f);

        Transform3 t = new Transform3((Vector3dc) pos, (Matrix3fc) mat);

        pos.set(9.0, 8.0, 7.0);

        assertVec3(t.pos(), 1.0, 2.0, 3.0);
    }

    @Test
    void copyConstructorIsDeepCopyMatrix() {
        Vector3d pos = new Vector3d(1.0, 2.0, 3.0);
        Matrix3f mat = new Matrix3f().rotationZ((float) Math.PI / 2.0f);

        Transform3 t = new Transform3((Vector3dc) pos, (Matrix3fc) mat);

        mat.identity();

        assertMatrix3(t.rotScale(),
                0f,  1f, 0f,
                -1f,  0f, 0f,
                0f,  0f, 1f);
    }

    @Test
    void setCopiesValues() {
        Transform3 src = new Transform3(
                new Vector3d(10.0, 20.0, 30.0),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );
        Transform3 dst = new Transform3();

        dst.set(src);

        assertVec3(dst.pos(), 10.0, 20.0, 30.0);
        assertMatrix3(dst.rotScale(),
                2f, 0f, 0f,
                0f, 3f, 0f,
                0f, 0f, 4f);
    }

    @Test
    void identityResetsTransform() {
        Transform3 dst = new Transform3(
                new Vector3d(10.0, 20.0, 30.0),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );

        dst.identity();

        assertVec3(dst.pos(), 0.0, 0.0, 0.0);
        assertMatrix3(dst.rotScale(),
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f);
    }

    @Test
    void translateWorksIndependently() {
        Transform3 t = new Transform3(
                new Vector3d(2.0, 3.0, 4.0),
                new Matrix3f().scaling(1.0f, 2.0f, 3.0f)
        );

        t.translate(1.0, 2.0, 3.0);

        assertVec3(t.pos(), 3.0, 5.0, 7.0);
    }

    @Test
    void scaleAroundWorksIndependently() {
        Transform3 t = new Transform3(
                new Vector3d(3.0, 5.0, 7.0),
                new Matrix3f().scaling(1.0f, 2.0f, 3.0f)
        );

        t.scaleAround(2.0f, 3.0f, 4.0f, 1.0, 1.0, 1.0);

        assertVec3(t.pos(), 5.0, 13.0, 25.0);
        assertMatrix3(t.rotScale(),
                2f, 0f, 0f,
                0f, 6f, 0f,
                0f, 0f, 12f);
    }

    @Test
    void mulProducesExpectedResult() {
        Transform3 a = new Transform3(
                new Vector3d(1.0, 2.0, 3.0),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );
        Transform3 b = new Transform3(
                new Vector3d(4.0, 5.0, 6.0),
                new Matrix3f().scaling(5.0f, 6.0f, 7.0f)
        );

        a.mul(b);

        assertVec3(a.pos(), 9.0, 17.0, 27.0);
        assertMatrix3(a.rotScale(),
                10f, 0f, 0f,
                0f, 18f, 0f,
                0f, 0f, 28f);
    }

    @Test
    void preMulProducesExpectedResult() {
        Transform3 b = new Transform3(
                new Vector3d(4.0, 5.0, 6.0),
                new Matrix3f().scaling(5.0f, 6.0f, 7.0f)
        );

        Transform3 c = new Transform3(
                new Vector3d(1.0, 2.0, 3.0),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );

        c.preMul(b);

        assertVec3(c.pos(), 9.0, 17.0, 27.0);
        assertMatrix3(c.rotScale(),
                10f, 0f, 0f,
                0f, 18f, 0f,
                0f, 0f, 28f);
    }

    @Test
    void invertProducesExpectedResult() {
        Transform3 d = new Transform3(
                new Vector3d(1.0, 2.0, 3.0),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );

        d.invert();

        // Target position is M_inv * (-p)
        assertVec3(d.pos(), -0.5, -2.0 / 3.0, -0.75);
        assertMatrix3(d.rotScale(),
                0.5f, 0f,   0f,
                0f,   1f/3f, 0f,
                0f,   0f,   0.25f);
    }

    @Test
    void rotateWorksIndependently() {
        Transform3 t = new Transform3(
                new Vector3d(10.0, 0.0, 0.0),
                new Matrix3f().identity()
        );

        Quaternionf q = new Quaternionf().rotateZ((float) (Math.PI / 2.0));
        t.rotate(q);

        assertVec3(t.pos(), 10.0, 0.0, 0.0);
        assertMatrix3(t.rotScale(),
                0f,  1f, 0f,
                -1f,  0f, 0f,
                0f,  0f, 1f);
    }

    @Test
    void rotateAroundWorksIndependently() {
        Transform3 p = new Transform3(
                new Vector3d(2.0, 1.0, 0.0),
                new Matrix3f().identity()
        );

        Quaternionf q = new Quaternionf().rotateZ((float) (Math.PI / 2.0));
        p.rotateAround(q, 1.0, 1.0, 0.0);

        assertVec3(p.pos(), 1.0, 2.0, 0.0);
        assertMatrix3(p.rotScale(),
                0f,  1f, 0f,
                -1f,  0f, 0f,
                0f,  0f, 1f);
    }

    @Test
    void getRelativeUsesRootSubtractionForTranslation() {
        Transform3 t = new Transform3(
                new Vector3d(1_000_000_000_000.25, -1_000_000_000_000.5, 1_000_000_000_000.75),
                new Matrix3f().scaling(2.0f, 3.0f, 4.0f)
        );

        Matrix4f out = new Matrix4f();
        t.getMatrix(1_000_000_000_000.0, -1_000_000_000_000.0, 1_000_000_000_000.0, out);

        assertEquals(2.0f, out.m00(), F_EPS);
        assertEquals(3.0f, out.m11(), F_EPS);
        assertEquals(4.0f, out.m22(), F_EPS);

        assertEquals(0.25f, out.m30(), F_EPS);
        assertEquals(-0.5f, out.m31(), F_EPS);
        assertEquals(0.75f, out.m32(), F_EPS);
    }
}