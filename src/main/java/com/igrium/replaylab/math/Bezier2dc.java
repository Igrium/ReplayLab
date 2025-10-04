package com.igrium.replaylab.math;

import org.joml.Vector2d;

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

    Vector2d sample(double t, Vector2d dest);

    Vector2d derive(double t, Vector2d dest);
    Vector2d derive2(double t, Vector2d dest);
}
