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
}
