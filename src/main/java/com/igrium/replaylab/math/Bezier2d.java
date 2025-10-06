package com.igrium.replaylab.math;

import org.joml.*;

import java.lang.Math;
import java.util.function.Consumer;

public class Bezier2d implements Bezier2dc {
    private static final double EPSILON = 1e-9;

    public double p0x;
    public double p0y;
    public double p1x;
    public double p1y;
    public double p2x;
    public double p2y;
    public double p3x;
    public double p3y;

    public Bezier2d() {}

    public Bezier2d(Bezier2dc other) {
        set(other);
    }

    public Bezier2d(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3) {
        setP0(p0);
        setP1(p1);
        setP2(p2);
        setP3(p3);
    }

    @Override
    public double p0x() {
        return p0x;
    }

    @Override
    public double p0y() {
        return p0y;
    }

    @Override
    public double p1x() {
        return p1x;
    }

    @Override
    public double p1y() {
        return p1y;
    }

    @Override
    public double p2x() {
        return p2x;
    }

    @Override
    public double p2y() {
        return p2y;
    }

    @Override
    public double p3x() {
        return p3x;
    }

    @Override
    public double p3y() {
        return p3y;
    }

    @Override
    public Vector2d getP0(Vector2d dest) {
        dest.x = p0x;
        dest.y = p0y;
        return dest;
    }

    @Override
    public Vector2d getP1(Vector2d dest) {
        dest.x = p1x;
        dest.y = p1y;
        return dest;
    }

    @Override
    public Vector2d getP2(Vector2d dest) {
        dest.x = p2x;
        dest.y = p2y;
        return dest;
    }

    @Override
    public Vector2d getP3(Vector2d dest) {
        dest.x = p3x;
        dest.y = p3y;
        return dest;
    }

    public Bezier2d set(Bezier2dc other) {
        p0x = other.p0x();
        p0y = other.p0y();
        p1x = other.p1x();
        p1y = other.p1y();
        p2x = other.p2x();
        p2y = other.p2y();
        p3x = other.p3x();
        p3y = other.p3y();
        return this;
    }

    public Bezier2d setP0(Vector2dc vec) {
        p0x = vec.x();
        p0y = vec.y();
        return this;
    }

    public Bezier2d setP1(Vector2dc vec) {
        p1x = vec.x();
        p1y = vec.y();
        return this;
    }

    public Bezier2d setP2(Vector2dc vec) {
        p2x = vec.x();
        p2y = vec.y();
        return this;
    }

    public Bezier2d setP3(Vector2dc vec) {
        p3x = vec.x();
        p3y = vec.y();
        return this;
    }

    public Bezier2d setP0(Vector2fc vec) {
        p0x = vec.x();
        p0y = vec.y();
        return this;
    }

    public Bezier2d setP1(Vector2fc vec) {
        p1x = vec.x();
        p1y = vec.y();
        return this;
    }

    public Bezier2d setP2(Vector2fc vec) {
        p2x = vec.x();
        p2y = vec.y();
        return this;
    }

    public Bezier2d setP3(Vector2fc vec) {
        p3x = vec.x();
        p3y = vec.y();
        return this;
    }

    @Override
    public Vector2d sample(double t, Vector2d dest) {
        dest.x = sample(t, p0x, p1x, p2x, p3x);
        dest.y = sample(t, p0y, p1y, p2y, p3y);
        return dest;
    }

    @Override
    public Vector2d derive(double t, Vector2d dest) {
        dest.x = derive(t, p0x, p1x, p2x, p3x);
        dest.y = derive(t, p0y, p1y, p2y, p3y);
        return dest;
    }

    @Override
    public Vector2d derive2(double t, Vector2d dest) {
        dest.x = derive2(t, p0x, p1x, p2x, p3x);
        dest.y = derive2(t, p0y, p1y, p2y, p3y);
        return dest;
    }

    /**
     * Sample a bezier on a single axis.
     */
    public static double sample(double t, double p0, double p1, double p2, double p3) {
        double invT = 1 - t;
        double invTSquared = invT * invT;
        double invTCubed = invTSquared * invT;
        double tSquared = t * t;
        double tCubed = tSquared * t;

        return invTCubed * p0 + 3 * invTSquared * t * p1 + 3 * invT * tSquared * p2 + tCubed * p3;
    }

    /**
     * Derive a bezier on a single axis.
     */
    public static double derive(double t, double p0, double p1, double p2, double p3) {
        double tSquared = t * t;
        double invTSquared = (1 - t) * (1 - t);

        return 3 * invTSquared * (p1 - p0) + 6 * (1 - t) * t * (p2 - p1) + 3 * tSquared * (p3 - p2);
    }

    /**
     * Get the second derivation of a bezier on a single axis.
     */
    public static double derive2(double t, double p0, double p1, double p2, double p3) {
        return 6 * (1 - t) * (p2 - 2 * p1 + p0) + 6 * t * (p3 - 2 * p2 + p1);
    }

    private static double aCoef(double p0, double p1, double p2, double p3) {
        return -p0 + 3 * p1 + -3 * p2 + p3;
    }

    private static double bCoef(double p0, double p1, double p2) {
        return 3 * p0 - 6 * p1 + 3 * p2;
    }

    private static double cCoef(double p0, double p1) {
        return -3 * p0 + 3 * p1;
    }

    public interface IntersectionConsumer {
        void accept(double x, double y);
    }


    /**
     * Find all the points where this bezier intersects with a given line segment.
     *
     * @param lineStart   Start point of the segment.
     * @param lineEnd     End point of the segment.
     * @param consumer    A consumer that gets called for every intersection. Presumably to be added to a list.
     * @param checkBounds If <code>true</code>, ensure that the point is actually on the segment.
     *                    Otherwise, all points on the line are accepted, regardless of whether they're in-bounds.
     */
    public void computeIntersections(Vector2dc lineStart, Vector2dc lineEnd, IntersectionConsumer consumer, boolean checkBounds) {
        double A = lineEnd.y() - lineStart.y(); //A=y2-y1
        double B = lineStart.x() - lineEnd.x(); //B=x1-x2
        double C = lineStart.x() * (lineStart.y() - lineEnd.y())
                 + lineStart.y() * (lineEnd.x() - lineStart.x()); //C=x1*(y1-y2)+y1*(x2-x1)

        double aCoefX = aCoef(p0x, p1x, p2x, p3x);
        double bCoefX = bCoef(p0x, p1x, p2x);
        double cCoefX = cCoef(p0x, p1x);
        double dCoefX = p0x;

        double aCoefY = aCoef(p0y, p1y, p2y, p3y);
        double bCoefY = bCoef(p0y, p1y, p2y);
        double cCoefY = cCoef(p0y, p1y);
        double dCoefY = p0y;

        double p0 = A * aCoefX + B * aCoefY;        // t^3
        double p1 = A * bCoefX + B * bCoefY;        // t^2;
        double p2 = A * cCoefX + B * cCoefY;        // t
        double p3 = A * dCoefX + B * dCoefY + C;    // 1

        Vector3d r = cubicRoots(p0, p1, p2, p3, new Vector3d());

        for (int i = 0; i < 3; i++) {
            double t = getVecIdx(r, i);
            if (Double.isNaN(t)) {
                continue;
            }

            double x = aCoefX * t * t * t + bCoefX * t * t + cCoefX * t + dCoefX;
            double y = aCoefY * t * t * t + bCoefY * t * t + cCoefY * t + dCoefY;

            double s;
            if (lineEnd.x() != lineStart.x()) { // if not vertical line
                s = (x - lineStart.x()) / (lineEnd.x() - lineStart.x());
            } else {
                s = (y - lineStart.y()) / (lineEnd.y() - lineStart.y());
            }

            // in-bounds check
            if (checkBounds && (t < -EPSILON || t > 1 + EPSILON || s < -EPSILON || s > 1 + EPSILON)) {
                continue;
            }

            consumer.accept(x, y);
        }
    }

    // adapted from https://www.particleincell.com/2013/cubic-line-intersection/

    private static final double SQRT_3 = Math.sqrt(3);

    /**
     * Compute the cubic roots of a bezier based on its coefficients on a single axis.
     * Handles degenerate cases (quadratic or linear).
     * @param a A coefficient
     * @param b B coefficient
     * @param c C coefficient
     * @param d D coefficient
     * @param dest A vector to store the results in. Any non-existent result will be NaN.
     * @return <code>dest</code>
     */
    private static Vector3d cubicRoots(double a, double b, double c, double d, Vector3d dest) {
        final double EPSILON = 1e-12;

        // Cubic case
        if (Math.abs(a) > EPSILON) {
            double A = b / a;
            double B = c / a;
            double C = d / a;

            double Q = (3 * B - A * A) / 9;
            double R = (9 * A * B - 27 * C - 2 * A * A * A) / 54;
            double D = Q * Q * Q + R * R;

            if (D >= 0) {
                double sqrtD = Math.sqrt(D);
                double S = sgn(R + sqrtD) * Math.pow(Math.abs(R + sqrtD), 1.0/3.0);
                double T = sgn(R - sqrtD) * Math.pow(Math.abs(R - sqrtD), 1.0/3.0);

                dest.x = -A / 3 + (S + T);
                dest.y = -A / 3 - (S + T) / 2;
                dest.z = -A / 3 - (S + T) / 2;
                double im = Math.abs(SQRT_3 * (S - T) / 2);

                // Discard complex roots
                if (im > EPSILON) {
                    dest.y = Double.NaN;
                    dest.z = Double.NaN;
                }
            } else {
                double th = Math.acos(R / Math.sqrt(-Math.pow(Q, 3)));
                double sqrtNegQ = Math.sqrt(-Q);
                dest.x = 2 * sqrtNegQ * Math.cos(th / 3) - A / 3;
                dest.y = 2 * sqrtNegQ * Math.cos((th + 2 * Math.PI) / 3) - A / 3;
                dest.z = 2 * sqrtNegQ * Math.cos((th + 4 * Math.PI) / 3) - A / 3;
            }
        } else if (Math.abs(b) > EPSILON) {
            // Quadratic case: b*t^2 + c*t + d = 0
            double discriminant = c * c - 4 * b * d;
            if (discriminant < -EPSILON) {
                dest.x = dest.y = dest.z = Double.NaN;
            } else {
                double sqrtDisc = Math.sqrt(Math.max(0, discriminant));
                dest.x = (-c + sqrtDisc) / (2 * b);
                dest.y = (-c - sqrtDisc) / (2 * b);
                dest.z = Double.NaN;
            }
        } else if (Math.abs(c) > EPSILON) {
            // Linear case: c*t + d = 0
            dest.x = -d / c;
            dest.y = dest.z = Double.NaN;
        } else {
            // Constant: d = 0
            // Infinite solutions if d == 0, else none
            dest.x = dest.y = dest.z = Double.NaN;
        }

        // discard out of spec roots
        final double ROOT_EPSILON = 1e-9;
        for (var i = 0; i < 3; i++) {
            double val = getVecIdx(dest, i);
            if (val < -ROOT_EPSILON || val > 1 + ROOT_EPSILON || Double.isNaN(val)) {
                setVecIdx(dest, i, Double.NaN);
            }
        }

        return dest;
    }

    private static int sgn(double x) {
        return x < 0 ? -1 : 1;
    }

    private static double getVecIdx(Vector3dc vec, int index) {
        return switch (index) {
            case 0 -> vec.x();
            case 1 -> vec.y();
            case 2 -> vec.z();
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    private static void setVecIdx(Vector3d vec, int index, double val) {
        switch (index) {
            case 0 -> vec.x = val;
            case 1 -> vec.y = val;
            case 2 -> vec.z = val;
        }
    }
}
