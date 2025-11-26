package com.igrium.replaylab.scene.key;

import com.igrium.replaylab.math.VectorUtils;
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
    public record LocalHandleRef(int keyIdx, int handleIdx) {};

    /**
     * Modify the handles of a channel according to their handle types.
     * @param channel The channel
     * @param draggingHandles The handles being dragged. Some handle types (aligned) change their behavior based on these.
     */
    public static void computeAutoHandles(KeyChannel channel, @Nullable Collection<LocalHandleRef> draggingHandles) {
        Keyframe[] keys = channel.getKeyframes().toArray(Keyframe[]::new);
        Arrays.sort(keys);
        computeAutoHandles(keys, draggingHandles);
    }

    /**
     * Modify the handles of a channel according to their handle types.
     * @param keys A sorted list of keyframes.
     * @param draggingHandles The handles being dragged. Some handle types (aligned) change their behavior based on these.
     */
    public static void computeAutoHandles(Keyframe[] keys, @Nullable Collection<LocalHandleRef> draggingHandles) {
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

}
