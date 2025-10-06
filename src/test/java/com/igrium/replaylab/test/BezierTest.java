package com.igrium.replaylab.test;


import com.igrium.replaylab.math.Bezier2d;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testIntersection_Simple() {
        // Simple Bezier: diagonal from (0,0) to (1,1) should intersect y=x at all points
        Bezier2d bezier = new Bezier2d(
                new Vector2d(0, 0), new Vector2d(0.33, 0.33),
                new Vector2d(0.66, 0.66), new Vector2d(1, 1)
        );
        List<Vector2d> intersections = new ArrayList<>();

        bezier.computeIntersections(
                new Vector2d(0, 0), new Vector2d(1, 1),
                (x, y) -> intersections.add(new Vector2d(x, y)), true
        );

        // Should intersect at t=0 and t=1, and possibly at intermediate points
        assertFalse(intersections.isEmpty(), "Must have intersection(s)");
        for (Vector2d pt : intersections) {
            assertEquals(pt.x, pt.y, EPSILON);
            assertTrue(pt.x >= 0 && pt.x <= 1);
            assertTrue(pt.y >= 0 && pt.y <= 1);
        }
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
        // Same as above but disables bounds check: should yield more points
        Bezier2d bezier = new Bezier2d(
                new Vector2d(-1, -1), new Vector2d(0, 0),
                new Vector2d(2, 2), new Vector2d(3, 3)
        );
        List<Vector2d> intersections = new ArrayList<>();
        bezier.computeIntersections(
                new Vector2d(0, 0), new Vector2d(1, 1),
                (x, y) -> intersections.add(new Vector2d(x, y)), false
        );
        // Should include intersections outside the [0,1] region
        assertFalse(intersections.isEmpty(), "Should detect intersections even out of bounds");
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
}
