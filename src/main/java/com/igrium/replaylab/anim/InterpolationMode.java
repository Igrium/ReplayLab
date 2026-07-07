package com.igrium.replaylab.anim;

import com.igrium.replaylab.math.Bezier2d;
import com.igrium.replaylab.math.Beziers;
import lombok.Getter;
import org.joml.Vector2dc;

public enum InterpolationMode {
    BEZIER(new Bezier()),
    LINEAR(new Linear()),
    CONSTANT(new Constant());

    private final Impl impl;

    @Getter
    private final String translationKey;

    InterpolationMode(Impl impl) {
        this.impl = impl;
        this.translationKey = "interp_mode." + name().toLowerCase();
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

    private interface Impl {
        double sample(Keyframe prev, Keyframe next, double timestamp);
        double integrate(Keyframe prev, Keyframe next, double cutoff);
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
    }

    private static double intersectLine(Vector2dc p1, Vector2dc p2, double x) {
        // m = (y2 - y1) / (x2 - x1)
        double m = (p2.y() - p1.y()) / (p2.x() - p1.x());
        // y = y1 + m * (x - x1)
        return m * (x - p1.x()) + p1.y();
    }
}
