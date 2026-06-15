package com.igrium.replaylab.math;

import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector3d;

public class FCurveHandleCalc {

    public static final byte HD_AUTOTYPE_NORMAL = 0;
    public static final byte HD_AUTOTYPE_LOCKED_FINAL = 1;

    public static class BezTriple {
        // vec[0] = Left Handle, vec[1] = Control Point, vec[2] = Right Handle
        public Vector3d[] vec = new Vector3d[]{new Vector3d(), new Vector3d(), new Vector3d()};
        public HandleType h1, h2;
        public byte auto_handle_type;

        public BezTriple fromKeyframe(Keyframe key) {
            vec[0].set(key.getGlobalAX(), key.getGlobalAY(), 0);
            vec[1].set(key.getCenter().x, key.getCenter().y, 0);
            vec[2].set(key.getGlobalBX(), key.getGlobalBY(), 0);

            h1 = key.getHandleAType();
            h2 = key.getHandleBType();

            return this;
        }

        public void toKeyframe(Keyframe key) {
            key.setGlobalA(vec[0].x, vec[0].y);
            key.getCenter().set(vec[1].x, vec[1].y);
            key.setGlobalB(vec[2].x, vec[2].y);

            key.setHandleAType(h1);
            key.setHandleBType(h2);
        }
    }

    // =========================================================================
    // Tridiagonal Matrix Solvers
    // =========================================================================

    public static boolean tridiagonalSolve(double[] a, double[] b, double[] c, double[] d, double[] r_x, int count) {
        if (count < 1) {
            return false;
        }

        double[] c1 = new double[count];
        double[] d1 = new double[count];
        double c_prev, d_prev, x_prev;

        // Forward pass
        c1[0] = c_prev = c[0] / b[0];
        d1[0] = d_prev = d[0] / b[0];

        for (int i = 1; i < count; i++) {
            double denum = b[i] - a[i] * c_prev;
            c1[i] = c_prev = c[i] / denum;
            d1[i] = d_prev = (d[i] - a[i] * d_prev) / denum;
        }

        // Back pass
        int i = count;
        x_prev = d_prev;
        r_x[--i] = x_prev;

        while (--i >= 0) {
            x_prev = d1[i] - c1[i] * x_prev;
            r_x[i] = x_prev;
        }

        return Double.isFinite(x_prev);
    }

    public static boolean tridiagonalSolveCyclic(double[] a, double[] b, double[] c, double[] d, double[] r_x,
                                                 int count) {
        if (count < 1) {
            return false;
        }

        if (count == 1) {
            r_x[0] = d[0] / (a[0] + b[0] + c[0]);
            return Double.isFinite(r_x[0]);
        }

        if (count == 2) {
            double[] a2 = {0, a[1] + c[1]};
            double[] c2 = {a[0] + c[0], 0};
            return tridiagonalSolve(a2, b, c2, d, r_x, count);
        }

        double a0 = a[0];
        double cN = c[count - 1];

        if (a0 == 0.0 && cN == 0.0) {
            return tridiagonalSolve(a, b, c, d, r_x, count);
        }

        double[] tmp = new double[count];
        double[] b2 = new double[count];

        System.arraycopy(b, 0, b2, 0, count);
        b2[0] -= a0;
        b2[count - 1] -= cN;

        tmp[0] = a0;
        tmp[count - 1] = cN;

        boolean success = tridiagonalSolve(a, b2, c, tmp, tmp, count) && tridiagonalSolve(a, b2, c, d, r_x, count);

        if (success) {
            double coeff = (r_x[0] + r_x[count - 1]) / (1.0 + tmp[0] + tmp[count - 1]);
            for (int i = 0; i < count; i++) {
                r_x[i] -= coeff * tmp[i];
            }
        }

        return success;
    }

    // =========================================================================
    // Core Bezier Thomas Algorithm Logic
    // =========================================================================

    private static double bezierRelaxDirection(double[] a, double[] b, double[] c, double[] d, double[] h, int i,
                                               int count) {
        double state = a[i] * h[(i + count - 1) % count] + b[i] * h[i] + c[i] * h[(i + 1) % count] - d[i];
        return -state * b[i];
    }

    private static void bezierLockUnknown(double[] a, double[] b, double[] c, double[] d, int i, double value) {
        a[i] = c[i] = 0.0;
        b[i] = 1.0;
        d[i] = value;
    }

    private static void bezierRestoreEquation(double[] a, double[] b, double[] c, double[] d, double[] a0,
                                              double[] b0, double[] c0, double[] d0, int i) {
        a[i] = a0[i];
        b[i] = b0[i];
        c[i] = c0[i];
        d[i] = d0[i];
    }

    private static boolean tridiagonalSolveWithLimits(double[] a, double[] b, double[] c, double[] d, double[] h,
                                                      double[] hmin, double[] hmax, int solveCount) {
        double[] a0 = new double[solveCount];
        double[] b0 = new double[solveCount];
        double[] c0 = new double[solveCount];
        double[] d0 = new double[solveCount];
        boolean[] isLocked = new boolean[solveCount];
        byte[] numUnlocks = new byte[solveCount];

        System.arraycopy(a, 0, a0, 0, solveCount);
        System.arraycopy(b, 0, b0, 0, solveCount);
        System.arraycopy(c, 0, c0, 0, solveCount);
        System.arraycopy(d, 0, d0, 0, solveCount);

        boolean overshoot, unlocked;

        do {
            if (!tridiagonalSolveCyclic(a, b, c, d, h, solveCount)) {
                return false;
            }

            boolean all = false;
            boolean locked = false;
            overshoot = unlocked = false;

            do {
                for (int i = 0; i < solveCount; i++) {
                    if (h[i] >= hmin[i] && h[i] <= hmax[i]) continue;

                    overshoot = true;
                    double target = h[i] > hmax[i] ? hmax[i] : hmin[i];

                    if (target != 0.0 || all) {
                        isLocked[i] = true;
                        bezierLockUnknown(a, b, c, d, i, target);
                        locked = true;
                    }
                }
                all = true;
            } while (overshoot && !locked);

            if (!locked) {
                for (int i = 0; i < solveCount; i++) {
                    if (!isLocked[i] || numUnlocks[i] >= 2) continue;

                    double relax = bezierRelaxDirection(a0, b0, c0, d0, h, i, solveCount);

                    if ((relax > 0 && h[i] < hmax[i]) || (relax < 0 && h[i] > hmin[i])) {
                        bezierRestoreEquation(a, b, c, d, a0, b0, c0, d0, i);
                        isLocked[i] = false;
                        numUnlocks[i]++;
                        unlocked = true;
                    }
                }
            }
        } while (overshoot || unlocked);

        return true;
    }

    private static void bezierEqContinuous(double[] a, double[] b, double[] c, double[] d, double[] dy, double[] l,
                                           int i) {
        a[i] = l[i] * l[i];
        b[i] = 2.0 * (l[i] + 1);
        c[i] = 1.0 / l[i + 1];
        d[i] = dy[i] * l[i] * l[i] + dy[i + 1];
    }

    private static void bezierEqNoaccelRight(double[] a, double[] b, double[] c, double[] d, double[] dy, double[] l,
                                             int i) {
        a[i] = 0.0;
        b[i] = 2.0;
        c[i] = 1.0 / l[i + 1];
        d[i] = dy[i + 1];
    }

    private static void bezierEqNoaccelLeft(double[] a, double[] b, double[] c, double[] d, double[] dy, double[] l,
                                            int i) {
        a[i] = l[i] * l[i];
        b[i] = 2.0 * l[i];
        c[i] = 0.0;
        d[i] = dy[i] * l[i] * l[i];
    }

    private static void bezierClamp(double[] hmax, double[] hmin, int i, double dy, boolean noReverse,
                                    boolean noOvershoot) {
        if (dy > 0) {
            if (noOvershoot) hmax[i] = Math.min(hmax[i], dy);
            if (noReverse) hmin[i] = 0.0;
        } else if (dy < 0) {
            if (noReverse) hmax[i] = 0.0;
            if (noOvershoot) hmin[i] = Math.max(hmin[i], dy);
        } else if (noReverse || noOvershoot) {
            hmax[i] = hmin[i] = 0.0;
        }
    }

    private static void bezierOutputHandleInner(BezTriple bezt, boolean right, Vector3d newval, boolean endpoint) {
        Vector3d tmp = new Vector3d();
        int idx = right ? 2 : 0;
        HandleType hr = right ? bezt.h2 : bezt.h1;
        HandleType hm = right ? bezt.h1 : bezt.h2;

        if (!(hr == HandleType.AUTO || hr == HandleType.AUTO_CLAMPED || hr == HandleType.VECTOR)) {
            return;
        }

        bezt.vec[idx].set(newval);

        if (hm == HandleType.ALIGNED) {
            double hlen = bezt.vec[1].distance(bezt.vec[2 - idx]);
            double h2len = bezt.vec[1].distance(bezt.vec[idx]);

            bezt.vec[1].sub(bezt.vec[idx], tmp);
            bezt.vec[2 - idx].set(bezt.vec[1]).add(tmp.mul(hlen / h2len));
        } else if (endpoint && (hm == HandleType.AUTO || hm == HandleType.AUTO_CLAMPED || hm == HandleType.VECTOR)) {
            bezt.vec[1].sub(bezt.vec[idx], tmp);
            bezt.vec[2 - idx].set(bezt.vec[1]).add(tmp);
        }
    }

    private static void bezierOutputHandle(BezTriple bezt, boolean right, double dy, boolean endpoint) {
        Vector3d tmp = new Vector3d(bezt.vec[right ? 2 : 0]);
        tmp.y = bezt.vec[1].y + dy;
        bezierOutputHandleInner(bezt, right, tmp, endpoint);
    }

    private static boolean bezierCheckSolveEndHandle(BezTriple bezt, HandleType htype, boolean end) {
        return (htype == HandleType.VECTOR) || (end && (htype == HandleType.AUTO || htype == HandleType.AUTO_CLAMPED) && bezt.auto_handle_type == HD_AUTOTYPE_NORMAL);
    }

    private static double bezierCalcHandleAdj(Vector2d hsize, double dx) {
        double fac = dx / (hsize.x + dx / 3.0);
        if (fac < 1.0) {
            hsize.mul(fac);
        }
        return 1.0 - 3.0 * hsize.x / dx;
    }

    private static void bezierHandleCalcSmoothFcurve(BezTriple[] bezt, int total, int start, int count, boolean cycle) {
        if (count < 2) return;

        int solveCount = count;
        boolean fullCycle = (start == 0 && count == total && cycle);

        BezTriple beztFirst = bezt[start];
        BezTriple beztLast = bezt[(start + count > total) ? start + count - total : start + count - 1];

        boolean solveFirst = bezierCheckSolveEndHandle(beztFirst, beztFirst.h2, start == 0);
        boolean solveLast = bezierCheckSolveEndHandle(beztLast, beztLast.h1, start + count == total);

        if (count == 2 && !fullCycle && solveFirst == solveLast) {
            return;
        }

        double[] dx = new double[count];
        double[] dy = new double[count];
        double[] l = new double[count];
        double[] a = new double[count];
        double[] b = new double[count];
        double[] c = new double[count];
        double[] d = new double[count];
        double[] h = new double[count];
        double[] hmax = new double[count];
        double[] hmin = new double[count];

        dx[0] = dy[0] = Double.NaN;

        for (int i = 1, j = start + 1; i < count; i++, j++) {
            dx[i] = bezt[j].vec[1].x - bezt[j - 1].vec[1].x;
            dy[i] = bezt[j].vec[1].y - bezt[j - 1].vec[1].y;

            if (cycle && j == total - 1) {
                j = 0;
            }
        }

        if (fullCycle) {
            dx[0] = dx[count - 1];
            dy[0] = dy[count - 1];
            l[0] = l[count - 1] = dx[1] / dx[0];
        } else {
            l[0] = l[count - 1] = 1.0;
        }

        for (int i = 1; i < count - 1; i++) {
            l[i] = dx[i + 1] / dx[i];
        }

        boolean clampedPrev = false;
        boolean clampedCur = (beztFirst.h1 == HandleType.AUTO_CLAMPED || beztFirst.h2 == HandleType.AUTO_CLAMPED);

        for (int i = 0; i < count; i++) {
            hmax[i] = Double.MAX_VALUE;
            hmin[i] = -Double.MAX_VALUE;
        }

        for (int i = 1, j = start + 1; i < count; i++, j++) {
            clampedPrev = clampedCur;
            clampedCur = (bezt[j].h1 == HandleType.AUTO_CLAMPED || bezt[j].h2 == HandleType.AUTO_CLAMPED);

            if (cycle && j == total - 1) {
                j = 0;
                clampedCur =
                        clampedCur || (bezt[j].h1 == HandleType.AUTO_CLAMPED || bezt[j].h2 == HandleType.AUTO_CLAMPED);
            }

            bezierClamp(hmax, hmin, i - 1, dy[i], clampedPrev, clampedPrev);
            bezierClamp(hmax, hmin, i, dy[i] * l[i], clampedCur, clampedCur);
        }

        double firstHandleAdj = 0.0, lastHandleAdj = 0.0;

        if (fullCycle) {
            int i = solveCount = count - 1;
            hmin[0] = Math.max(hmin[0], hmin[i]);
            hmax[0] = Math.min(hmax[0], hmax[i]);

            solveFirst = solveLast = true;
            bezierEqContinuous(a, b, c, d, dy, l, 0);
        } else {
            Vector2d tmp = new Vector2d();

            if (!solveFirst) {
                tmp.set(beztFirst.vec[2].x - beztFirst.vec[1].x, beztFirst.vec[2].y - beztFirst.vec[1].y);
                firstHandleAdj = bezierCalcHandleAdj(tmp, dx[1]);
                bezierLockUnknown(a, b, c, d, 0, tmp.y);
            } else {
                bezierEqNoaccelRight(a, b, c, d, dy, l, 0);
            }

            if (!solveLast) {
                tmp.set(beztLast.vec[1].x - beztLast.vec[0].x, beztLast.vec[1].y - beztLast.vec[0].y);
                lastHandleAdj = bezierCalcHandleAdj(tmp, dx[count - 1]);
                bezierLockUnknown(a, b, c, d, count - 1, tmp.y);
            } else {
                bezierEqNoaccelLeft(a, b, c, d, dy, l, count - 1);
            }
        }

        for (int i = 1; i < count - 1; i++) {
            bezierEqContinuous(a, b, c, d, dy, l, i);
        }

        if (!fullCycle) {
            if (count > 2 || solveLast) b[1] += l[1] * firstHandleAdj;
            if (count > 2 || solveFirst) b[count - 2] += lastHandleAdj;
        }

        if (tridiagonalSolveWithLimits(a, b, c, d, h, hmin, hmax, solveCount)) {
            if (fullCycle) h[count - 1] = h[0];

            for (int i = 1, j = start + 1; i < count - 1; i++, j++) {
                boolean end = (j == total - 1);
                bezierOutputHandle(bezt[j], false, -h[i] / l[i], end);

                if (end) j = 0;

                bezierOutputHandle(bezt[j], true, h[i], end);
            }

            if (solveFirst) bezierOutputHandle(beztFirst, true, h[0], start == 0);
            if (solveLast) bezierOutputHandle(beztLast, false, -h[count - 1] / l[count - 1], start + count == total);
        }
    }

    private static boolean isFreeAutoPoint(BezTriple bezt) {
        return (bezt.h1 == HandleType.AUTO || bezt.h1 == HandleType.AUTO_CLAMPED || bezt.h2 == HandleType.AUTO || bezt.h2 == HandleType.AUTO_CLAMPED) && bezt.auto_handle_type == HD_AUTOTYPE_NORMAL;
    }

    public static void nurbHandleSmoothFcurve(Keyframe[] keys) {
        BezTriple[] bez = new BezTriple[keys.length];
        for (int i = 0; i < keys.length; i++) {
            bez[i] = new BezTriple().fromKeyframe(keys[i]);
        }

        nurbHandleSmoothFcurve(bez, bez.length, false);

        for (int i = 0; i < keys.length; i++) {
            bez[i].toKeyframe(keys[i]);
        }
    }

    public static void nurbHandleSmoothFcurve(BezTriple[] bezt, int total, boolean cyclic) {
        cyclic = cyclic && isFreeAutoPoint(bezt[0]) && isFreeAutoPoint(bezt[total - 1]);

        int searchBase = 0;

        if (cyclic) {
            for (int i = 1; i < total - 1; i++) {
                if (!isFreeAutoPoint(bezt[i])) {
                    searchBase = i;
                    break;
                }
            }

            if (searchBase == 0) {
                bezierHandleCalcSmoothFcurve(bezt, total, 0, total, cyclic);
                return;
            }
        }

        int start = searchBase;
        int count = 1;

        for (int i = 1, j = start + 1; i < total; i++, j++) {
            if (j == total - 1 && cyclic) j = 0;

            if (!isFreeAutoPoint(bezt[j])) {
                bezierHandleCalcSmoothFcurve(bezt, total, start, count + 1, cyclic);
                start = j;
                count = 1;
            } else {
                count++;
            }
        }

        if (count > 1) {
            bezierHandleCalcSmoothFcurve(bezt, total, start, count, cyclic);
        }
    }
}