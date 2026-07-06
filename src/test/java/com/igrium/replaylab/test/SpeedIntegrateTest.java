package com.igrium.replaylab.test;

import com.igrium.replaylab.scene.key.ChannelUtils;
import com.igrium.replaylab.scene.key.KeyChannel;
import com.igrium.replaylab.scene.key.Keyframe;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the speed-curve integration that {@code ReplayScene.sceneToReplayTime} relies on.
 * A speed channel is integrated to map scene time onto replay time; that mapping must stay
 * monotonic (replay time can never run backwards) and match the analytic value for simple cases.
 */
public class SpeedIntegrateTest {

    private static final double EPS = 1e-6;

    private static KeyChannel channel(HandleType type, double[]... keys) {
        KeyChannel chan = new KeyChannel();
        for (double[] k : keys) {
            Keyframe key = new Keyframe((int) k[0], k[1]);
            key.setHandleType(type);
            chan.getKeyframes().add(key);
        }
        ChannelUtils.computeHandles(chan, null);
        return chan;
    }

    @Test
    public void constantSpeedMapsOneToOne() {
        KeyChannel chan = channel(HandleType.AUTO_CLAMPED, new double[]{0, 1.0}, new double[]{5000, 1.0});
        for (int t = 0; t <= 10000; t += 1000) {
            assertEquals(t, chan.integrate(t), EPS, "constant speed 1 should map replay time 1:1 at " + t);
        }
    }

    @Test
    public void singleKeyframeScalesLinearly() {
        KeyChannel chan = channel(HandleType.AUTO_CLAMPED, new double[]{3000, 2.0});
        for (int t = 0; t <= 6000; t += 1000) {
            assertEquals(2.0 * t, chan.integrate(t), EPS, "single speed-2 keyframe should integrate to 2t at " + t);
        }
    }

    @Test
    public void integrationIsAlwaysMonotonic() {
        // Non-negative speed keyframes with a variety of handle types must never make the
        // integral (replay time) decrease as scene time advances.
        for (HandleType type : HandleType.values()) {
            KeyChannel chan = channel(type,
                    new double[]{0, 5.0}, new double[]{5000, 0.05}, new double[]{10000, 5.0});
            double prev = Double.NEGATIVE_INFINITY;
            for (int t = 0; t <= 10000; t += 100) {
                double v = chan.integrate(t);
                assertTrue(v >= prev - EPS,
                        "replay time went backwards with " + type + " at t=" + t + " (" + v + " < " + prev + ")");
                prev = v;
            }
        }
    }
}
