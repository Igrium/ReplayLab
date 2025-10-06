package com.igrium.replaylab.math;

import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector2fc;
import org.joml.Vector3d;

/**
 * A simple cubic Bézier curve polynomial form
 */
public interface Bezier2dc {
    double p0x();
    double p0y();
    double p1x();
    double p1y();
    double p2x();
    double p2y();
    double p3x();
    double p3y();

    Vector2d getP0(Vector2d dest);
    Vector2d getP1(Vector2d dest);
    Vector2d getP2(Vector2d dest);
    Vector2d getP3(Vector2d dest);

    /**
     * Sample the curve at a given T value.
     * @param t T to sample at.
     * @param dest Vector to put the sampled point.
     * @return <code>dest</code>
     */
    Vector2d sample(double t, Vector2d dest);

    /**
     * Derive the curve at a given T value.
     * @param t T to sample at.
     * @param dest Vector to put the derivation in
     * @return <code>dest</code>
     */
    Vector2d derive(double t, Vector2d dest);

    /**
     * Get the second derivation of the curve at a given T value.
     * @param t T to sample at.
     * @param dest Vector to put the second derivation in
     * @return <code>dest</code>
     */
    Vector2d derive2(double t, Vector2d dest);

    /**
     * Find all the points where this bezier intersects with a given line segment.
     *
     * @param lineStart   Start point of the segment.
     * @param lineEnd     End point of the segment.
     * @param consumer    A consumer that gets called for every intersection. Presumably to be added to a list.
     * @param checkBounds If <code>true</code>, ensure that the point is actually on the segment.
     *                    Otherwise, all points on the line are accepted, regardless of whether they're in-bounds.
     */
    void computeIntersections(Vector2dc lineStart, Vector2dc lineEnd, IntersectionConsumer consumer, boolean checkBounds);

    /**
     * Compute the cubic roots for this bezier on the X axis.
     * @param dest A vector to store the results in. Any non-existent root will be NaN.
     * @return <code>dest</code>
     */
    Vector3d xCubicRoots(Vector3d dest);

    /**
     * Compute the cubic roots for this bezier on the Y axis.
     * @param dest A vector to store the results in. Any non-existent root will be NaN.
     * @return <code>dest</code>
     */
    Vector3d yCubicRoots(Vector3d dest);

    interface IntersectionConsumer {
        void accept(double x, double y);
    }
}
