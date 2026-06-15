package com.igrium.replaylab.scene.key;

import com.igrium.replaylab.math.VectorUtils;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2dc;

/**
 * A number of "operators" regarding channels moved out here to de-bloat
 */
@UtilityClass
public class ChannelUtils {

    /**
     * I really wish Java had tuples sometimes
     */
    public record LocalHandleRef(int keyIdx, int handleIdx) {
    }

    /**
     * Modify the handles of a channel according to their handle types.
     *
     * @param channel         The channel
     * @param draggingHandles The handles being dragged. Some handle types (aligned) change their behavior based on
     * these.
     */
    public static void recomputeHandles(KeyChannel channel, @Nullable Collection<LocalHandleRef> draggingHandles) {
        Keyframe[] keys = channel.getKeyframes().toArray(Keyframe[]::new);
        Arrays.sort(keys);
        recomputeHandles(keys, draggingHandles);
    }

    /**
     * Modify the handles of a channel according to their handle types.
     *
     * @param keys            A sorted list of keyframes.
     * @param draggingHandles The handles being dragged. Some handle types (aligned) change their behavior based on
     * these.
     */
    public static void recomputeHandles(Keyframe[] keys, @Nullable Collection<LocalHandleRef> draggingHandles) {
        // Adapted from Blender's "legacy" handle generation.

        // The tangent that each keyframe will want in auto mode.
        double[] tangents = new double[keys.length];

        // Pre-compute tangents
        for (int i = 0; i < keys.length; i++) {
            if (0 < i && i < keys.length - 1) {
                double dt = keys[i + 1].getTime() - keys[i - 1].getTime();
                tangents[i] = (keys[i + 1].getValue() - keys[i - 1].getValue()) / dt;
            } else {
                tangents[i] = 0; // Start and end keys are always flat for auto mode
            }
        }

        for (int keyIdx = 0; keyIdx < keys.length; keyIdx++) {
            Keyframe key = keys[keyIdx];

            for (int handleIdx = 1; handleIdx <= 2; handleIdx++) {
                HandleType type = handleIdx == 1 ? key.getHandleAType() : key.getHandleBType();

                switch (type) {
                    case AUTO, AUTO_CLAMPED -> {
                        double tangent = tangents[keyIdx];
                        if (keyIdx > 0 && handleIdx == 1) {
                            // incoming handle
                            double prevDt = key.getTime() - keys[keyIdx - 1].getTime();
                            double offsetTime = prevDt / 3.0;
                            double offsetValue = tangent * offsetTime;
                            // left handle is negative offset from the key point
                            key.getHandleA().set(-offsetTime, -offsetValue);

                        } else if (keyIdx < keys.length - 1 && handleIdx == 2) {
                            // outgoing handle
                            double nextDt = keys[keyIdx + 1].getTime() - key.getTime();
                            double offsetTime = nextDt / 3.0;
                            double offsetValue = tangent * offsetTime;
                            key.getHandleB().set(offsetTime, offsetValue);
                        }
                        // TODO: Clamping
                    }

                    case ALIGNED -> {
                        if (draggingHandles != null && draggingHandles.contains(new LocalHandleRef(keyIdx, handleIdx)))
                            continue; // Don't do alignment on currently-dragging handle

                        Vector2d vec = new Vector2d();
                        vec.set(handleIdx == 1 ? key.getHandleB() : key.getHandleA()); // Other handle
                        vec.normalize();

                        Vector2d handleRef = handleIdx == 1 ? key.getHandleA() : key.getHandleB();
                        double handleLength = handleRef.length();
                        handleRef.set(vec).mul(-1).normalize(handleLength);
                    }

                    case VECTOR -> {
                        // TODO: Take incoming bezier into account
                        if (keyIdx > 0 && handleIdx == 1) {
                            // incoming handle
                            double prevDt = keys[keyIdx - 1].getTime() - key.getTime();
                            double offsetTime = prevDt / 3.0;

                            Vector2d vec = keys[keyIdx - 1].getCenter().sub(key.getCenter(), new Vector2d());
                            VectorUtils.setXKeepDirection(offsetTime, vec);
                            key.getHandleA().set(vec);
                        } else if (keyIdx < keys.length - 1 && handleIdx == 2) {
                            // outgoing handle
                            double nextDt = keys[keyIdx + 1].getTime() - key.getTime();
                            double offsetTime = nextDt / 3.0;

                            Vector2d vec = keys[keyIdx + 1].getCenter().sub(key.getCenter(), new Vector2d());
                            VectorUtils.setXKeepDirection(offsetTime, vec);
                            key.getHandleB().set(vec);
                        }
                    }
                }
            }
        }
    }

    public static void computeAutoHandles(KeyChannel channel, @Nullable Collection<LocalHandleRef> draggingHandles) {
        Keyframe[] keys = channel.getKeyframes().toArray(Keyframe[]::new);
        Arrays.sort(keys);
        computeAutoHandles(keys, draggingHandles);
    }

    public static void computeAutoHandles(Keyframe[] keys, @Nullable Collection<LocalHandleRef> draggingHandles) {
        if (keys.length < 2) return;
        List<int[]> autoRanges = splitAutoRanges(keys);

        for (int[] range : autoRanges) {
            if (range[1] - range[0] < 2) continue;

            int knotCount = range[1] - range[0];
            Vector2d[] knots = new Vector2d[knotCount];
            for (int i = 0; i < knotCount; i++) {
                knots[i] = new Vector2d(keys[range[0] + i].getCenter());
            }

            Vector2d[] lefts = new Vector2d[knotCount - 1];
            Vector2d[] rights = new Vector2d[knotCount - 1];

            // Default to Natural Splines (NaN) to prevent boundary freakouts.
            Vector2d startTangent = new Vector2d(Double.NaN, Double.NaN);
            Vector2d endTangent = new Vector2d(Double.NaN, Double.NaN);

            // Call the 2D solver!
            computeControlPoints(knots, lefts, rights, startTangent, endTangent);

            for (int i = 0; i < lefts.length; i++) {
                Keyframe key = keys[range[0] + i];
                Keyframe next = keys[range[0] + i + 1];

                // Set the absolute global positions directly from the 2D solver
                key.getHandleB().set(lefts[i].x - key.getCenter().x, lefts[i].y - key.getCenter().y);
                next.getHandleA().set(rights[i].x - next.getCenter().x, rights[i].y - next.getCenter().y);
            }
        }
    }

    private static List<int[]> splitAutoRanges(Keyframe[] keys) {
        List<int[]> continuousAutoKeys = new ArrayList<>();
        int rangeStart = -1;
        for (int i = 0; i < keys.length; i++) {
            Keyframe key = keys[i];
            if (key.getHandleAType() == HandleType.AUTO && key.getHandleBType() == HandleType.AUTO) {
                if (rangeStart == -1) {
                    rangeStart = i;
                }
            } else {
                if (rangeStart != -1) {
                    continuousAutoKeys.add(new int[]{rangeStart, i});
                    rangeStart = -1;
                }
            }
        }
        if (rangeStart != -1) {
            continuousAutoKeys.add(new int[]{rangeStart, keys.length});
        }
        return continuousAutoKeys;
    }

    private static void computeControlPoints(Vector2dc[] knots, Vector2d[] dest1, Vector2d[] dest2,
                                             Vector2dc startTangent, Vector2dc endTangent) {
        if (knots.length < 2) {
            return;
        }
        if (dest1.length != dest2.length || dest1.length != knots.length - 1) {
            throw new IllegalArgumentException("Dest arrays must be equal to knots.length - 1");
        }

        int n = knots.length - 1;
        double[] h = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = knots[i + 1].x() - knots[i].x();
            if (h[i] < 1e-6) {
                h[i] = 1e-6; // Guard against duplicate timestamps / division by zero
            }
        }

        double[] k = new double[knots.length];
        double[] p1 = new double[dest1.length];
        double[] p2 = new double[dest2.length];

        // X axis
        for (int i = 0; i < k.length; i++) {
            k[i] = knots[i].x();
        }
        computeControlPoints(k, h, p1, p2, startTangent.x(), endTangent.x());
        for (int i = 0; i < p1.length; i++) {
            dest1[i] = new Vector2d(p1[i], 0);
            dest2[i] = new Vector2d(p2[i], 0);
        }

        // Y axis
        for (int i = 0; i < k.length; i++) {
            k[i] = knots[i].y();
        }
        computeControlPoints(k, h, p1, p2, startTangent.y(), endTangent.y());
        for (int i = 0; i < p1.length; i++) {
            dest1[i].y = p1[i];
            dest2[i].y = p2[i];
        }
    }

    /**
     * Compute the control points of a collection of 1D Bézier knots using a non-uniform Thomas algorithm.
     * * @param k            Knot points
     * @param h            Knot spacing intervals (time differences)
     * @param p1           First handle of each segment
     * @param p2           Second handle of each segment
     * @param startTangent Outgoing tangent at the first knot
     * @param endTangent   Incoming tangent at the last knot
     */
    private static void computeControlPoints(double[] k, double[] h, double[] p1, double[] p2, double startTangent,
                                             double endTangent) {
        if (k.length < 2) return;
        int n = k.length - 1;

        boolean clampStart = !Double.isNaN(startTangent);
        boolean clampEnd = !Double.isNaN(endTangent);

        // Special case: Single segment
        if (n == 1) {
            p1[0] = clampStart ? k[0] + h[0] * startTangent / 3.0 : (2.0 * k[0] + k[1]) / 3.0;
            p2[0] = clampEnd ? k[1] - h[0] * endTangent / 3.0 : (k[0] + 2.0 * k[1]) / 3.0;
            return;
        }

        double[] a = new double[n];
        double[] b = new double[n];
        double[] c = new double[n];
        double[] r = new double[n];

        // Left-most row (i = 0)
        if (clampStart) {
            a[0] = 0;
            b[0] = 1;
            c[0] = 0;
            r[0] = k[0] + h[0] * startTangent / 3.0;
        } else {
            a[0] = 0;
            b[0] = 2;
            c[0] = h[0] / h[1];
            r[0] = k[0] + (1.0 + h[0] / h[1]) * k[1];
        }

        // Interior rows (i = 1 to n-2)
        for (int i = 1; i < n - 1; i++) {
            double hRatioPrev = h[i] / h[i - 1];
            a[i] = hRatioPrev * hRatioPrev;
            b[i] = 2.0 * (hRatioPrev + 1.0);
            c[i] = h[i] / h[i + 1];
            r[i] = (hRatioPrev + 1.0) * (hRatioPrev + 1.0) * k[i] + (1.0 + h[i] / h[i + 1]) * k[i + 1];
        }

        // Right-most row (i = n - 1)
        if (clampEnd) {
            double hRatioPrev = h[n - 1] / h[n - 2];
            a[n - 1] = hRatioPrev * hRatioPrev;
            b[n - 1] = 2.0 * (hRatioPrev + 1.0);
            c[n - 1] = 0;
            r[n - 1] = (hRatioPrev + 1.0) * (hRatioPrev + 1.0) * k[n - 1] + k[n] - h[n - 1] * endTangent / 3.0;
        } else {
            double hRatioPrev = h[n - 1] / h[n - 2];
            a[n - 1] = 2.0 * hRatioPrev * hRatioPrev;
            b[n - 1] = 4.0 * hRatioPrev + 3.0;
            c[n - 1] = 0;
            r[n - 1] = 2.0 * (hRatioPrev + 1.0) * (hRatioPrev + 1.0) * k[n - 1] + k[n];
        }

        // Forward sweep (Thomas elimination)
        for (int i = 1; i < n; i++) {
            double m = a[i] / b[i - 1];
            b[i] -= m * c[i - 1];
            r[i] -= m * r[i - 1];
        }

        // Back substitution to solve for p1
        p1[n - 1] = r[n - 1] / b[n - 1];
        for (int i = n - 2; i >= 0; --i) {
            p1[i] = (r[i] - c[i] * p1[i + 1]) / b[i];
        }

        // Compute p2 from p1 using non-uniform C1 continuity conditions
        for (int i = 0; i < n - 1; i++) {
            p2[i] = (1.0 + h[i] / h[i + 1]) * k[i + 1] - (h[i] / h[i + 1]) * p1[i + 1];
        }

        // Last p2 boundary calculation
        p2[n - 1] = clampEnd ? k[n] - h[n - 1] * endTangent / 3.0
                : 0.5 * (k[n] + p1[n - 1]);
    }
}