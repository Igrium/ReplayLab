package com.igrium.replaylab.scene.key;

import com.igrium.replaylab.math.FCurveHandleCalc;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;

import com.igrium.replaylab.scene.key.Keyframe.HandleType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;

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

    public static void computeHandles(KeyChannel channel, @Nullable Collection<LocalHandleRef> draggingHandles) {
        Keyframe[] keys = channel.getKeyframes().toArray(Keyframe[]::new);
        Arrays.sort(keys);
        FCurveHandleCalc.nurbHandleSmoothFcurve(keys);

        // Re-align aligned handles
        for (int keyIdx = 0; keyIdx < keys.length; keyIdx++) {
            Keyframe key = keys[keyIdx];
            for (int handleIdx = 1; handleIdx <= 2; handleIdx++) {
                HandleType type = handleIdx == 1 ? key.getHandleAType() : key.getHandleBType();

                if (type == HandleType.ALIGNED) {
                    if (draggingHandles != null && draggingHandles.contains(new LocalHandleRef(keyIdx, handleIdx)))
                        continue; // Don't do alignment on currently-dragging handle

                    Vector2d vec = new Vector2d();
                    vec.set(handleIdx == 1 ? key.getHandleB() : key.getHandleA()); // Other handle
                    vec.normalize();

                    Vector2d handleRef = handleIdx == 1 ? key.getHandleA() : key.getHandleB();
                    double handleLength = handleRef.length();
                    handleRef.set(vec).mul(-1).normalize(handleLength);
                }
            }
        }
    }

//    public static HandleType getDragHandleType(HandleType type, HandleTyp)
}