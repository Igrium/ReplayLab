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
     *                        these.
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
     *                        these.
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
        if (keys.length < 2) {
            return;
        }
        List<int[]> autoRanges = splitAutoRanges(keys);

        for (var range : autoRanges) {
            // Left boundary condition: look at the keyframe right before the range start
            Vector2d startTangent = range[0] > 0 ? keys[range[0] - 1].getGlobalB(new Vector2d()) :
                    new Vector2d(Double.NaN);

            // FIX 1: Look at keys[range[1]] instead of keys[range[1] + 1]
            Vector2d endTangent = range[1] < keys.length ? keys[range[1]].getGlobalA(new Vector2d()) :
                    new Vector2d(Double.NaN);

            Vector2dc[] knots = new Vector2dc[range[1] - range[0]];
            for (int i = 0; i < range[1] - range[0]; i++) {
                // FIX 2: Use range[0] + i to correctly map the range elements
                knots[i] = keys[range[0] + i].getCenter();
            }

            Vector2d[] lefts = new Vector2d[knots.length - 1];
            Vector2d[] rights = new Vector2d[knots.length - 1];

            computeControlPoints(knots, lefts, rights, startTangent, endTangent);

            for (int i = 0; i < lefts.length; i++) {
                keys[range[0] + i].setGlobalB(lefts[i]);
                // FIX 3: Use range[0] + i + 1 to stay inside the proper subset
                keys[range[0] + i + 1].setGlobalA(rights[i]);
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

        double[] k = new double[knots.length];
        double[] p1 = new double[dest1.length];
        double[] p2 = new double[dest2.length];

        // X axis
        for (int i = 0; i < k.length; i++) {
            k[i] = knots[i].x();
        }
        computeControlPoints(k, p1, p2, startTangent.x(), endTangent.x());
        for (int i = 0; i < p1.length; i++) {
            dest1[i] = new Vector2d(p1[i], 0);
            dest2[i] = new Vector2d(p2[i], 0);
        }

        // Y axis
        for (int i = 0; i < k.length; i++) {
            k[i] = knots[i].y();
        }
        computeControlPoints(k, p1, p2, startTangent.y(), endTangent.y());
        for (int i = 0; i < p1.length; i++) {
            dest1[i].y = p1[i];
            dest2[i].y = p2[i];
        }
    }

    /**
     * Compute the control points of a collection of 1D Bézier knots using the Thomas algorithm.
     * Either endpoint may use a clamped (fixed-tangent) or natural (zero-second-derivative)
     * boundary condition.
     *
     * <p>Pass {@link Double#NaN} for either tangent to use the natural boundary condition at
     * that end. The tangent values are in the same coordinate units as the knot points:
     * a tangent {@code T} at the start means the curve leaves {@code k[0]} in direction {@code T}
     * with speed {@code |T|}. If you only want to fix the direction, scale {@code T} to a
     * magnitude comparable to the adjacent chord length.
     *
     * <p>Adapted from <a href="https://www.particleincell.com/2012/bezier-splines/">particleincell.com</a>
     *
     * @param k            Knot points
     * @param p1           First handle of each segment; length must equal {@code k.length - 1}
     * @param p2           Second handle of each segment; length must equal {@code k.length - 1}
     * @param startTangent Outgoing tangent at the first knot in global coordinate space (i.e. the
     *                     curve derivative B'(0)), or {@link Double#NaN} for natural BC. Magnitude
     *                     matters: scale to the adjacent chord length if only fixing direction.
     * @param endTangent   Incoming tangent at the last knot in global coordinate space (i.e. B'(1)),
     *                     or {@link Double#NaN} for natural BC.
     */
    private static void computeControlPoints(double[] k, double[] p1, double[] p2, double startTangent,
                                             double endTangent) {
        if (k.length < 2) {
            throw new IllegalArgumentException("Must have at least 2 knots");
        }
        int n = k.length - 1;

        if (p1.length != p2.length || p1.length != n) {
            throw new IllegalArgumentException("Dest arrays must be equal to knots.length - 1");
        }

        boolean clampStart = !Double.isNaN(startTangent);
        boolean clampEnd = !Double.isNaN(endTangent);

        // -------------------------------------------------------------------------
        // Special case: a single segment has no interior knots, so the start and end
        // boundary conditions are independent and each handle is solved directly.
        //   Clamped:  B'(0) = T  →  p1[0] = k[0] + T/3
        //             B'(1) = T  →  p2[0] = k[1] - T/3
        //   Natural:  B''(0) = 0 →  p1[0] = (2k[0] + k[1]) / 3
        //             B''(1) = 0 →  p2[0] = (k[0] + 2k[1]) / 3
        // -------------------------------------------------------------------------
        if (n == 1) {
            p1[0] = clampStart ? k[0] + startTangent / 3.0 : (2 * k[0] + k[1]) / 3.0;
            p2[0] = clampEnd ? k[1] - endTangent / 3.0 : (k[0] + 2 * k[1]) / 3.0;
            return;
        }

        double[] a = new double[n];
        double[] b = new double[n];
        double[] c = new double[n];
        double[] r = new double[n];

        // -------------------------------------------------------------------------
        // Left-most row
        //
        // Natural (original):
        //   From B''_0(0) = 0 → 2·p1[0] + p1[1] = k[0] + 2·k[1]
        //
        // Clamped:
        //   B'_0(0) = 3·(p1[0] - k[0]) = startTangent  →  p1[0] = k[0] + T/3
        //   Row becomes a trivial identity; the forward sweep carries this into row 1.
        // -------------------------------------------------------------------------
        if (clampStart) {
            a[0] = 0;
            b[0] = 1;
            c[0] = 0;
            r[0] = k[0] + startTangent / 3.0;
        } else {
            a[0] = 0;
            b[0] = 2;
            c[0] = 1;
            r[0] = k[0] + 2 * k[1];
        }

        // Interior rows — unchanged; these enforce C1/C2 continuity at every internal knot
        for (int i = 1; i < n - 1; i++) {
            a[i] = 1;
            b[i] = 4;
            c[i] = 1;
            r[i] = 4 * k[i] + 2 * k[i + 1];
        }

        // -------------------------------------------------------------------------
        // Right-most row
        //
        // Natural (original):
        //   From B''_{n-1}(1) = 0, combined with eq. (4): 2·p1[n-2] + 7·p1[n-1] = 8·k[n-1] + k[n]
        //
        // Clamped:
        //   B'_{n-1}(1) = 3·(k[n] - p2[n-1]) = endTangent  →  p2[n-1] = k[n] - T/3
        //   Substituting into equation (4): p1[n-1] - 2·p2[n-1] + k[n] = 0
        //   →  p1[n-1] = k[n] - 2·T/3
        //   Row becomes a trivial identity.
        // -------------------------------------------------------------------------
        if (clampEnd) {
            a[n - 1] = 0;
            b[n - 1] = 1;
            c[n - 1] = 0;
            r[n - 1] = k[n] - 2.0 * endTangent / 3.0;
        } else {
            a[n - 1] = 2;
            b[n - 1] = 7;
            c[n - 1] = 0;
            r[n - 1] = 8 * k[n - 1] + k[n];
        }

        // Forward sweep
        // Note: the original code used i < n-1, which skipped elimination of a[n-1] and
        // produced incorrect results for n >= 2. The correct upper bound is i < n.
        for (int i = 1; i < n; i++) {
            double m = a[i] / b[i - 1];
            b[i] -= m * c[i - 1];
            r[i] -= m * r[i - 1];
        }

        // Back substitution
        p1[n - 1] = r[n - 1] / b[n - 1];
        for (int i = n - 2; i >= 0; --i)
            p1[i] = (r[i] - c[i] * p1[i + 1]) / b[i];

        // Compute p2 from p1 using C1 continuity at interior knots: 2·k[i+1] = p1[i+1] + p2[i]
        for (int i = 0; i < n - 1; i++)
            p2[i] = 2 * k[i + 1] - p1[i + 1];

        // Last p2 depends on the end boundary condition
        p2[n - 1] = clampEnd ? k[n] - endTangent / 3.0      // B'_{n-1}(1) = endTangent
                : 0.5 * (k[n] + p1[n - 1]);   // B''_{n-1}(1) = 0  (natural)
    }

}
