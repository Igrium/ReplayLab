package com.igrium.replaylab.math;

import org.joml.Vector2d;
import org.joml.Vector2dc;

public class VectorUtils {
    private static final double EPS = 1e-12;

    public static Vector2d setXKeepDirection(double targetX, Vector2dc vec, Vector2d dest) {
        dest.set(vec);
        if (Math.abs(dest.x) <= EPS) {
            if (Math.abs(targetX) <= EPS) {
                return dest; // Do nothing
            } else {
                throw new ArithmeticException("Impossible: vertical direction must be non-zero.");
            }
        }

        double scalar = targetX / dest.x;
        dest.x *= scalar;
        dest.y *= scalar;
        return dest;
    }

    public static Vector2d setXKeepDirection(double targetX, Vector2d vec) {
        return setXKeepDirection(targetX, vec, vec);
    }
}
