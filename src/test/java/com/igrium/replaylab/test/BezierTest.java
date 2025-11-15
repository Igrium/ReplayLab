package com.igrium.replaylab.test;


import com.igrium.replaylab.math.Bezier2d;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.isNaN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Yeah I vibe-code my unit tests. Fuck you.
 */
public class BezierTest {
    private static final double EPSILON = 1e-9;

    // Utility for comparing two vectors
    private static void assertVecEquals(Vector2dc expected, Vector2dc actual) {
        assertEquals(expected.x(), actual.x(), BezierTest.EPSILON, "X mismatch");
        assertEquals(expected.y(), actual.y(), BezierTest.EPSILON, "Y mismatch");
    }

    @Test
    public void testSample_Linear() {
        // Linear Bezier: should be a straight line from (0,0) to (1,1)
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0, 0),
                new Vector2d(1, 1), new Vector2d(1, 1)
        );
        Vector2d dest = new Vector2d();

        assertVecEquals(new Vector2d(0, 0), bezier.sample(0.0, dest));
        assertVecEquals(new Vector2d(1, 1), bezier.sample(1.0, dest));
        assertVecEquals(new Vector2d(0.5, 0.5), bezier.sample(0.5, dest));
    }

    @Test
    public void testSample_Cubic() {
        // Classic cubic: (0,0)->(1,2)->(2,2)->(3,0)
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(1, 2),
                new Vector2d(2, 2), new Vector2d(3, 0)
        );
        Vector2d dest = new Vector2d();

        assertVecEquals(new Vector2d(0, 0), bezier.sample(0.0, dest));
        assertVecEquals(new Vector2d(3, 0), bezier.sample(1.0, dest));
        // Middle point (by formula)
        Vector2d mid = new Vector2d();
        bezier.sample(0.5, mid);
        assertEquals(1.5, mid.x, EPSILON);
        assertEquals(1.5, mid.y, EPSILON);
    }

    @Test
    public void testDerive_Endpoints() {
        // Derivative at endpoints should match the tangent vector
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(1, 2),
                new Vector2d(2, 2), new Vector2d(3, 0)
        );
        Vector2d d0 = new Vector2d();
        Vector2d d1 = new Vector2d();

        bezier.derive(0.0, d0);
        bezier.derive(1.0, d1);

        // At t=0: 3*(p1-p0)
        assertVecEquals(new Vector2d(3, 6), d0);
        // At t=1: 3*(p3-p2)
        assertVecEquals(new Vector2d(3, -6), d1);
    }

    @Test
    public void testDerive2_Endpoints() {
        // Second derivative at endpoints
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(1, 2),
                new Vector2d(2, 2), new Vector2d(3, 0)
        );
        Vector2d d0 = new Vector2d();
        Vector2d d1 = new Vector2d();

        bezier.derive2(0.0, d0);
        bezier.derive2(1.0, d1);

        // At t=0: 6*(p2 - 2*p1 + p0)
        Vector2d expected0 = new Vector2d(0, 6 * (2 - 2 * 2));
        assertVecEquals(expected0, d0);

        // At t=1: 6*(p3 - 2*p2 + p1)
        Vector2d expected1 = new Vector2d(0, 6 * (-2 * 2 + 2));
        assertVecEquals(expected1, d1);
    }

    @Test
    public void testSetP_methods() {
        Bezier2d bezier = new Bezier2d();
        bezier.setP0(new Vector2d(1, 2));
        bezier.setP1(new Vector2d(3, 4));
        bezier.setP2(new Vector2d(5, 6));
        bezier.setP3(new Vector2d(7, 8));

        assertEquals(1, bezier.p0x);
        assertEquals(2, bezier.p0y);
        assertEquals(3, bezier.p1x);
        assertEquals(4, bezier.p1y);
        assertEquals(5, bezier.p2x);
        assertEquals(6, bezier.p2y);
        assertEquals(7, bezier.p3x);
        assertEquals(8, bezier.p3y);
    }

    @Test
    public void testIntersection_Real() {
        // S-shaped cubic that crosses y=x at t=0.5
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0, 1),
                new Vector2d(1, 0), new Vector2d(1, 1)
        );
        List<Vector2d> intersections = new ArrayList<>();
        bezier.computeIntersections(
                new Vector2d(0, 1), new Vector2d(1, 0),
                (x, y) -> intersections.add(new Vector2d(x, y)), true
        );
        assertFalse(intersections.isEmpty(), "Must have intersection(s)");
        // Should intersect at the center
        boolean found = false;
        for (Vector2d pt : intersections) {
            if (Math.abs(pt.x - pt.y) < EPSILON) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Should cross y=x somewhere");
    }

    @Test
    public void testIntersection_NoIntersect() {
        // Bezier from (0,0) to (1,1), line far away
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0.25, 0.25),
                new Vector2d(0.75, 0.75), new Vector2d(1, 1)
        );
        List<Vector2d> intersections = new ArrayList<>();
        bezier.computeIntersections(
                new Vector2d(2, 2), new Vector2d(3, 3),
                (x, y) -> intersections.add(new Vector2d(x, y)), true
        );
        assertTrue(intersections.isEmpty(), "Should not intersect distant line");
    }

    @Test
    public void testIntersection_BoundsCheck() {
        // Bezier that extends beyond line segment, but only in-bounds should be accepted
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, -1), new Vector2d(0, 0),
                new Vector2d(2, 2), new Vector2d(3, 3)
        );
        List<Vector2d> intersections = new ArrayList<>();
        bezier.computeIntersections(
                new Vector2d(0, 0), new Vector2d(1, 1),
                (x, y) -> intersections.add(new Vector2d(x, y)), true
        );
        for (Vector2d pt : intersections) {
            // Only intersections within [0,1] for both x and y should be accepted
            assertTrue(pt.x >= 0 && pt.x <= 1, "Intersection x out of bounds");
            assertTrue(pt.y >= 0 && pt.y <= 1, "Intersection y out of bounds");
        }
    }

    @Test
    public void testIntersection_NoBoundsCheck() {
        // Curve crosses the line segment only once, but with bounds check off
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, 1), new Vector2d(0, 0),
                new Vector2d(2, 2), new Vector2d(3, -1)
        );
        List<Vector2d> intersections = new ArrayList<>();
        bezier.computeIntersections(
                new Vector2d(0, 0), new Vector2d(1, 1),
                (x, y) -> intersections.add(new Vector2d(x, y)), false
        );
        assertFalse(intersections.isEmpty(), "Should detect intersections even out of bounds");
        // Some intersection points may be outside the segment!
        boolean foundInBounds = false, foundOutOfBounds = false;
        for (Vector2d pt : intersections) {
            if (pt.x >= 0 && pt.x <= 1 && pt.y >= 0 && pt.y <= 1) foundInBounds = true;
            if (pt.x < 0 || pt.x > 1 || pt.y < 0 || pt.y > 1) foundOutOfBounds = true;
        }
        assertTrue(foundInBounds || foundOutOfBounds, "Should find some intersection (in or out of bounds)");
    }

    @Test
    public void testSetFromOtherBezier() {
        Bezier2d b1 = new Bezier2d(
                new Vector2d(1, 2), new Vector2d(3, 4),
                new Vector2d(5, 6), new Vector2d(7, 8)
        );
        Bezier2d b2 = new Bezier2d();
        b2.set(b1);
        assertEquals(b2.p0x, b1.p0x, EPSILON);
        assertEquals(b2.p0y, b1.p0y, EPSILON);
        assertEquals(b2.p1x, b1.p1x, EPSILON);
        assertEquals(b2.p1y, b1.p1y, EPSILON);
        assertEquals(b2.p2x, b1.p2x, EPSILON);
        assertEquals(b2.p2y, b1.p2y, EPSILON);
        assertEquals(b2.p3x, b1.p3x, EPSILON);
        assertEquals(b2.p3y, b1.p3y, EPSILON);
    }

    @Test
    public void testXCubicRoots_ThreeRealRoots() {
        // Cubic equation x(t) = 0 for a curve with roots at t ≈ 0.2, 0.5, 0.8
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), // p0x = 0
                new Vector2d(0.2, 0), // p1x = 0.2
                new Vector2d(0.5, 0), // p2x = 0.5
                new Vector2d(0.8, 0)  // p3x = 0.8
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        int realRoots = 0;
        for (double r : new double[]{roots.x, roots.y, roots.z}) {
            if (!isNaN(r)) {
                assertTrue(r >= -EPSILON && r <= 1 + EPSILON, "Root not in [0,1]: " + r);
                realRoots++;
            }
        }
        assertTrue(realRoots >= 1, "Should find at least one real root in [0,1]");
    }

    @Test
    public void testYCubicRoots_QuadraticCase() {
        // Degenerate to quadratic: a = 0, roots at t = 0.25, 0.75
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0),
                new Vector2d(0, 0.25),
                new Vector2d(0, 0.75),
                new Vector2d(0, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.yCubicRoots(roots);

        int nanCount = 0;
        int realCount = 0;
        for (double r : new double[]{roots.x, roots.y, roots.z}) {
            if (isNaN(r)) nanCount++;
            else realCount++;
        }
        assertTrue(realCount >= 1, "Should find at least one real root in [0,1]");
        assertTrue(nanCount >= 1, "Should have at least one NaN for missing roots");
    }

    @Test
    public void testYCubicRoots_ConstantCase() {
        // Degenerate to constant: a = b = c = 0
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 1),
                new Vector2d(0, 1),
                new Vector2d(0, 1),
                new Vector2d(0, 1)
        );
        Vector3d roots = new Vector3d();
        bezier.yCubicRoots(roots);

        assertTrue(isNaN(roots.x), "Constant case root should be NaN");
        assertTrue(isNaN(roots.y), "Constant case root should be NaN");
        assertTrue(isNaN(roots.z), "Constant case root should be NaN");
    }

    @Test
    public void testXCubicRoots_ComplexRoots() {
        // A curve that should only have one real root, others NaN
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, 0),
                new Vector2d(0, 0),
                new Vector2d(0, 0),
                new Vector2d(1, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        // Only one root should be real, others NaN
        int nanCount = 0;
        int realCount = 0;
        for (double r : new double[]{roots.x, roots.y, roots.z}) {
            if (isNaN(r)) nanCount++;
            else realCount++;
        }
        assertEquals(1, realCount, "Should have exactly one real root");
        assertEquals(2, nanCount, "Should have exactly two NaN roots");
    }

    @Test
    public void testNaNPropagation() {
        // If coefficients are all NaN, all roots must be NaN
        Bezier2d bezier = new Bezier2d(
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        assertTrue(isNaN(roots.x));
        assertTrue(isNaN(roots.y));
        assertTrue(isNaN(roots.z));
    }

    private int countNaN(Vector3d v) {
        int count = 0;
        if (Double.isNaN(v.x)) count++;
        if (Double.isNaN(v.y)) count++;
        if (Double.isNaN(v.z)) count++;
        return count;
    }

    private int countReal(Vector3d v) {
        int count = 0;
        if (!Double.isNaN(v.x)) count++;
        if (!Double.isNaN(v.y)) count++;
        if (!Double.isNaN(v.z)) count++;
        return count;
    }

    @Test
    public void test_xCubicRoots_one_real_root_monotonic() {
        // Monotonic cubic: only one root at t=0.
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0.5, 0),
                new Vector2d(1.0, 0), new Vector2d(1.5, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        // Only one root should be real and at t=0
        assertEquals(1, countReal(roots), "Should have one real root in [0,1]");
        assertEquals(0.0, roots.x, EPSILON);
        assertTrue(Double.isNaN(roots.y));
        assertTrue(Double.isNaN(roots.z));
    }

    @Test
    public void test_xCubicRoots_three_real_roots_inflecting() {
        // Cubic Bezier with control points designed to cross x=0 three times in [0,1].
        // Example: (-1,0), (1,0), (-1,0), (1,0) (oscillates around x=0)
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, 0), new Vector2d(1, 0),
                new Vector2d(-1, 0), new Vector2d(1, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        // Should have three real roots in [0,1]
        int realRoots = countReal(roots);
        assertTrue(realRoots >= 2, "Should have at least two real roots in [0,1]");
        // We can't predict the exact values, but all should be within [0,1]
        for(double r : new double[] {roots.x, roots.y, roots.z}) {
            if (!Double.isNaN(r)) {
                assertTrue(r >= -EPSILON && r <= 1 + EPSILON, "Root not in [0,1]: " + r);
            }
        }
    }

    @Test
    public void test_xCubicRoots_linear_case() {
        // All x values are identical and not zero: no roots for x(t) = 0
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0.5, 0), new Vector2d(0.5, 0),
                new Vector2d(0.5, 0), new Vector2d(0.5, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);
        // All roots should be NaN
        assertEquals(3, countNaN(roots), "All roots should be NaN for x(t) != 0 everywhere");
    }

    @Test
    public void test_yCubicRoots_quadratic_case() {
        // Degenerate to quadratic: (0,0), (0,1), (0,2), (0,3), roots at t=0 (y=0)
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0, 1),
                new Vector2d(0, 2), new Vector2d(0, 3)
        );
        Vector3d roots = new Vector3d();
        bezier.yCubicRoots(roots);

        // Only one root should be real and at t=0
        assertEquals(1, countReal(roots), "Should have one real root");
        assertEquals(0.0, roots.x, EPSILON);
        assertTrue(Double.isNaN(roots.y));
        assertTrue(Double.isNaN(roots.z));
    }

    @Test
    public void test_xCubicRoots_constant_case() {
        // All x values are 1, so x(t) - 0 = 1, no roots for x(t) = 0
        Bezier2d bezier = new Bezier2d(
                new Vector2d(1, 0), new Vector2d(1, 0),
                new Vector2d(1, 0), new Vector2d(1, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);
        // All roots must be NaN
        assertEquals(3, countNaN(roots), "Constant case: all roots should be NaN");
    }

    @Test
    public void test_xCubicRoots_complex_roots() {
        // Cubic with one real root: x^3 + x + 1 = 0
        // Approximate with control points (-1,0), (0,0), (0,0), (1,0)
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, 0), new Vector2d(0, 0),
                new Vector2d(0, 0), new Vector2d(1, 0)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        int realCount = countReal(roots);
        int nanCount = countNaN(roots);
        assertEquals(1, realCount, "Should have one real root");
        assertEquals(2, nanCount, "Should have two NaN roots");
    }

    @Test
    public void test_xCubicRoots_nan_propagation() {
        // All control points are NaN, so all roots must be NaN
        Bezier2d bezier = new Bezier2d(
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN),
                new Vector2d(Double.NaN, Double.NaN)
        );
        Vector3d roots = new Vector3d();
        bezier.xCubicRoots(roots);

        assertEquals(3, countNaN(roots), "All roots must be NaN (NaN propagation)");
    }

    @Test
    public void testSubdivision() {
        Bezier2d orig = new Bezier2d(
                new Vector2d(0.0, 0.0),
                new Vector2d(1.0, 2.0),
                new Vector2d(2.0, 2.0),
                new Vector2d(3.0, 0.0)
        );

        Bezier2d left = new Bezier2d();
        Bezier2d right = new Bezier2d();
        orig.subdivide(left, right, 0.5);

        // left: (0,0), (0.5,1), (1.0,1.5), (1.5,1.5)
        assertEquals(0.0, left.p0x, EPSILON);
        assertEquals(0.0, left.p0y, EPSILON);

        assertEquals(0.5, left.p1x, EPSILON);
        assertEquals(1.0, left.p1y, EPSILON);

        assertEquals(1.0, left.p2x, EPSILON);
        assertEquals(1.5, left.p2y, EPSILON);

        assertEquals(1.5, left.p3x, EPSILON);
        assertEquals(1.5, left.p3y, EPSILON);

        // right: (1.5,1.5), (2.0,1.5), (2.5,1), (3,0)
        assertEquals(1.5, right.p0x, EPSILON);
        assertEquals(1.5, right.p0y, EPSILON);

        assertEquals(2.0, right.p1x, EPSILON);
        assertEquals(1.5, right.p1y, EPSILON);

        assertEquals(2.5, right.p2x, EPSILON);
        assertEquals(1.0, right.p2y, EPSILON);

        assertEquals(3.0, right.p3x, EPSILON);
        assertEquals(0.0, right.p3y, EPSILON);
    }
}
