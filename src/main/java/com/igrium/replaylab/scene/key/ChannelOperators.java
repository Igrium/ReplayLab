package com.igrium.replaylab.scene.key;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import com.igrium.replaylab.scene.key.Keyframe.HandleType;

/**
 * A number of "operators" regarding channels moved out here to de-bloat
 */
@UtilityClass
public class ChannelOperators {

    /**
     * Automatically generate the handles for keyframes with their handle types set to auto
     * @param keys A sorted list of keyframes
     */
    public static void computeAutoHandles(Keyframe[] keys) {
        // Adapted from Blender's "legacy" handle generation
        double[] tangents = new double[keys.length];

        for (int i = 0; i < keys.length; i++) {
            double dt;
            if (i == 0) {
                // Forward difference
                dt = keys[1].getTime() - keys[0].getTime();
                tangents[i] = ((keys[i].getValue() - keys[0].getValue())) / dt;
            } else if (i == keys.length - 1) {
                dt = keys[i].getTime() - keys[i - 1].getTimeInt();
                tangents[i] = (keys[i].getValue() - keys[i - 1].getValue()) / dt;
            } else {
                dt = keys[i + 1].getTime() - keys[i - 1].getTime();
                tangents[i] = (keys[i + 1].getValue() - keys[i - 1].getValue()) / dt;
            }
        }

        for (int i = 0; i < keys.length; i++) {
            Keyframe key = keys[i];
            double tangent = tangents[i];

            // incoming (left) handle
            if (i > 0 && (key.getHandleAType() == HandleType.AUTO)) {
                double prevDt = key.getTime() - keys[i - 1].getTime();
                double offsetTime = prevDt / 3.0;
                double offsetValue = tangent * offsetTime;
                // left handle is negative offset from the key point
                key.getHandleA().set(-offsetTime, -offsetValue);
            }


            // outgoing (right) handle
            if (i < keys.length - 1 && (key.getHandleBType() == HandleType.AUTO)) {
                double nextDt = keys[i + 1].getTime() - key.getTime();
                double offsetTime = nextDt / 3.0;
                double offsetValue = tangent * offsetTime;
                key.getHandleB().set(offsetTime, offsetValue);
            }
        }
    }

    public static void computeAutoHandles(KeyChannel channel) {
        Keyframe[] keyframes = channel.getKeyframes().toArray(Keyframe[]::new);
        Arrays.sort(keyframes);
        computeAutoHandles(keyframes);
    }
}
