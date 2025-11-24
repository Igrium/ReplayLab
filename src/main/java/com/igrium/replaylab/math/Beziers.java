package com.igrium.replaylab.math;

import com.igrium.replaylab.scene.key.Keyframe;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Beziers {
    /**
     * Compute the Bezier curve between two keyframes.
     * @param left Left keyframe
     * @param right Right keyframe
     * @param dest Destination bezier object
     * @return <code>dest</code>
     */
    public static Bezier2d fromKeyframes(Keyframe left, Keyframe right, Bezier2d dest) {
        dest.setP0(left.getCenter());
        dest.setP3(right.getCenter());

        dest.p1x = left.getGlobalBX();
        dest.p1y = left.getGlobalBY();

        dest.p2x = right.getGlobalAX();
        dest.p2y = right.getGlobalAY();

        return dest;
    }

    /**
     * Update a keyframe so that it acts as the left side of a bezier.
     *
     * @param keyframe Keyframe to update.
     * @param bezier   Bezier to use.
     */
    public static void toLeftKeyframe(Keyframe keyframe, Bezier2dc bezier) {
        bezier.getP0(keyframe.getCenter());
        keyframe.setGlobalB(bezier.p1x(), bezier.p1y());
    }

    /**
     * Update a keyframe so that it acts as the right side of a bezier.
     *
     * @param keyframe Keyframe to update.
     * @param bezier   Bezier to use.
     */
    public static void toRightKeyframe(Keyframe keyframe, Bezier2dc bezier) {
        bezier.getP3(keyframe.getCenter());
        keyframe.setGlobalA(bezier.p2x(), bezier.p2y());
    }

    /**
     * Find the parameter t in [0,1] such that the Bezier curve's X coordinate equals the given xSample.
     *
     * <p>Uses a simple Newton–Raphson iteration with a linear initial guess (based on end points)
     * to invert the cubic Bezier's X component. The result is clamped to [0,1]. Iteration stops
     * when the error is below a small epsilon, the derivative is too small, or a max iteration
     * count is reached.</p>
     *
     * @param bezier the Bezier curve to invert (provides p0x..p3x and sampling/derivative helpers)
     * @param xSample the X value to find the corresponding parameter t for
     * @return a value t in [0,1] such that bezier.sampleX(t) ≈ xSample; if convergence fails
     *         the best found/clamped t is returned
     * @implNote I vibe-coded this because I hate math. Bite me.
     */
    public static double intersectX(Bezier2dc bezier, double xSample) {
        // Initial guess assumes a linear mapping
        double t = (xSample - bezier.p0x()) / (bezier.p3x() - bezier.p0x());
        t = Math.max(0.0, Math.min(1.0, t)); // Clamp to [0, 1]

        // Newton-Raphson iterations
        final int MAX_ITER = 7;
        final double EPS = 1e-9;
        for (int i = 0; i < MAX_ITER; i++) {
            double bx = bezier.sampleX(t); // Evaluate Bezier at t
            double dbx = Bezier2d.derive(t, bezier.p0x(), bezier.p1x(), bezier.p2x(), bezier.p3x()); // Derivative

            double err = bx - xSample;
            if (Math.abs(err) < EPS) {
                break; // Converged!
            }
            if (Math.abs(dbx) < EPS) {
                // Derivative too close to zero; fallback to previous t
                break;
            }
            t -= err / dbx;
            t = Math.max(0.0, Math.min(1.0, t));
        }
        return t;
    }
}
