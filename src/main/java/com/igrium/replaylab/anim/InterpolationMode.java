package com.igrium.replaylab.anim;

import com.igrium.replaylab.math.Bezier2d;
import com.igrium.replaylab.math.Beziers;
import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector2dc;

public enum InterpolationMode implements StringRepresentable {
    BEZIER(new Bezier()),
    LINEAR(new Linear()),
    CONSTANT(new Constant());

    public static final Codec<InterpolationMode> CODEC = StringRepresentable.fromEnum(InterpolationMode::values);

    private final Impl impl;

    @Getter
    private final String translationKey;

    InterpolationMode(Impl impl) {
        this.impl = impl;
        this.translationKey = "interp_mode." + name().toLowerCase();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    /**
     * Sample this line segment
     *
     * @param prev      First keyframe
     * @param next      Second keyframe
     * @param timestamp Global timestamp to sample at
     * @return The sampled value
     */
    public double sample(Keyframe prev, Keyframe next, double timestamp) {
        return impl.sample(prev, next, timestamp);
    }

    /**
     * Compute the integral of this line segment up till a given point.
     *
     * @param prev   First keyframe
     * @param next   Second keyframe
     * @param cutoff Global timestamp to cut integral off at.
     * @return The integral.
     */
    public double integrate(Keyframe prev, Keyframe next, double cutoff) {
        return impl.integrate(prev, next, cutoff);
    }

    /**
     * Inverse of <code>integrate</code>. Find the timestamp where the integral of this segment reaches an area.
     * Assumes the curve is non-negative on this segment and that the area is within the segment's total integral.
     *
     * @param prev First keyframe
     * @param next Second keyframe
     * @param area Target area, measured from <code>prev</code>.
     * @return The global timestamp where the integral equals the area.
     */
    public double inverseIntegrate(Keyframe prev, Keyframe next, double area) {
        return impl.inverseIntegrate(prev, next, area);
    }

    private interface Impl {
        double sample(Keyframe prev, Keyframe next, double timestamp);
        double integrate(Keyframe prev, Keyframe next, double cutoff);
        double inverseIntegrate(Keyframe prev, Keyframe next, double area);
    }

    private static class Bezier implements Impl {

        @Override
        public double sample(Keyframe prev, Keyframe next, double timestamp) {
            Bezier2d bezier = Beziers.fromKeyframes(prev, next, new Bezier2d());
            double t = Beziers.intersectX(bezier, timestamp);
            return bezier.sampleY(t);
        }

        @Override
        public double integrate(Keyframe prev, Keyframe next, double cutoff) {
            Bezier2d bezier = Beziers.fromKeyframes(prev, next, new Bezier2d());
            double t;
            if (cutoff < prev.getTime()) {
                t = 0;
            } else if (cutoff > next.getTime()) {
                t = 1;
            } else {
                t = Beziers.intersectX(bezier, cutoff);
            }

            return Beziers.integrateCurve(bezier, t);

        }

        @Override
        public double inverseIntegrate(Keyframe prev, Keyframe next, double area) {
            if (area <= 0) return prev.getTime();
            Bezier2d bezier = Beziers.fromKeyframes(prev, next, new Bezier2d());

            // The integral is a degree 6 polynomial in t with no closed form inverse.
            // Solve with Newton, falling back to bisection when a step leaves the bracket.
            double total = Beziers.integrateCurve(bezier, 1);
            if (area >= total) return next.getTime();

            final int MAX_ITER = 50;
            final double EPS = 1e-9 * Math.max(1, area);

            double lo = 0;
            double hi = 1;
            double t = area / total;
            for (int i = 0; i < MAX_ITER; i++) {
                double err = Beziers.integrateCurve(bezier, t) - area;
                if (Math.abs(err) < EPS) break;

                if (err > 0) hi = t;
                else lo = t;

                // Derivative of the integral is y(t) * x'(t)
                double deriv = bezier.sampleY(t) * Bezier2d.derive(t, bezier.p0x, bezier.p1x, bezier.p2x, bezier.p3x);
                double nextT = t - err / deriv;
                t = (deriv > 0 && nextT > lo && nextT < hi) ? nextT : 0.5 * (lo + hi);
            }
            return bezier.sampleX(t);
        }
    }

    private static class Linear implements Impl {

        @Override
        public double sample(Keyframe prev, Keyframe next, double timestamp) {
            return intersectLine(prev.getCenter(), next.getCenter(), timestamp);
        }

        @Override
        public double integrate(Keyframe prev, Keyframe next, double cutoff) {
            double x1 = prev.getCenter().x;
            double y1 = prev.getCenter().y;

            double x2 = Math.min(next.getCenter().x, cutoff);
            double y2 = intersectLine(prev.getCenter(), next.getCenter(), x2);

            return (y1 + y2) / 2.0 * (x2 - x1);
        }

        @Override
        public double inverseIntegrate(Keyframe prev, Keyframe next, double area) {
            double x1 = prev.getCenter().x;
            if (area <= 0) return x1;

            double v1 = prev.getCenter().y;
            double m = (next.getCenter().y - v1) / (next.getCenter().x - x1);

            // Solve area = v1 * u + m * u^2 / 2 for u.
            // This form of the quadratic formula stays stable when m is near zero.
            double disc = Math.max(0, v1 * v1 + 2 * m * area);
            double denom = v1 + Math.sqrt(disc);
            if (denom <= 0) return next.getCenter().x;

            return Math.min(x1 + 2 * area / denom, next.getCenter().x);
        }
    }

    private static class Constant implements Impl {

        @Override
        public double sample(Keyframe prev, Keyframe next, double timestamp) {
            return prev.getValue();
        }

        @Override
        public double integrate(Keyframe prev, Keyframe next, double cutoff) {
            double endTime = Math.min(next.getTime(), cutoff);
            return prev.getValue() * (endTime - prev.getTime());
        }

        @Override
        public double inverseIntegrate(Keyframe prev, Keyframe next, double area) {
            if (area <= 0) return prev.getTime();
            if (prev.getValue() <= 0) return next.getTime();
            return Math.min(prev.getTime() + area / prev.getValue(), next.getTime());
        }
    }

    private static double intersectLine(Vector2dc p1, Vector2dc p2, double x) {
        // m = (y2 - y1) / (x2 - x1)
        double m = (p2.y() - p1.y()) / (p2.x() - p1.x());
        // y = y1 + m * (x - x1)
        return m * (x - p1.x()) + p1.y();
    }
}
