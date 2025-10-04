package com.igrium.replaylab.math;

import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector2fc;

public class Bezier2d implements Bezier2dc {
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
        p0x = vec.y();
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

    private static double sample(double t, double p0, double p1, double p2, double p3) {
        double invT = 1 - t;
        double invTSquared = invT * invT;
        double invTCubed = invTSquared * invT;
        double tSquared = t * t;
        double tCubed = tSquared * t;

        return invTCubed * p0 + 3 * invTSquared * t * p1 + 3 * invT * tSquared * p2 + tCubed * p3;
    }

    private static double derive(double t, double p0, double p1, double p2, double p3) {
        double tSquared = t * t;
        double invTSquared = (1 - t) * (1 - t);

        return 3 * invTSquared * (p1 - p0) + 6 * (1 - t) * t * (p2 - p1) + 3 * tSquared * (p3 - p2);
    }

    public static double derive2(double t, double p0, double p1, double p2, double p3) {
        return 6 * (1 - t) * (p2 - 2 * p1 + p0) + 6 * t * (p3 - 2 * p2 + p1);
    }
}
