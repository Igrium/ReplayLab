package com.igrium.replaylab.anim;

import com.igrium.replaylab.math.FCurveHandleCalc;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;

import com.igrium.replaylab.anim.Keyframe.HandleType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.slf4j.LoggerFactory;

/**
 * A number of "operators" regarding channels moved out here to de-bloat
 */
@UtilityClass
public class ChannelUtils {

    /**
     * I really wish Java had tuples sometimes
     */
    public record LocalHandleRef(int keyIdx, int handleIdx) {
        private HandleType getType(Keyframe key) {
            return handleIdx == 1 ? key.getHandleAType() : key.getHandleBType();
        }

        private HandleType getOtherType(Keyframe key) {
            return handleIdx == 1 ? key.getHandleBType() : key.getHandleAType();
        }

        private void setType(Keyframe key, HandleType type) {
            if (handleIdx == 1) key.setHandleAType(type);
            else key.setHandleBType(type);
        }

        private void setOtherType(Keyframe key, HandleType type) {
            if (handleIdx == 1) key.setHandleBType(type);
            else key.setHandleAType(type);
        }

        private Vector2d getVecRef(Keyframe key) {
            return handleIdx == 1 ? key.getHandleA() : key.getHandleB();
        }

        private Vector2d getOtherVecRef(Keyframe key) {
            return handleIdx == 1 ? key.getHandleB() : key.getHandleA();
        }
    }

    public static void computeHandles(KeyChannel channel, @Nullable Collection<LocalHandleRef> draggingHandles) {
        Keyframe[] keys = channel.getKeyframes().toArray(Keyframe[]::new);

        if (draggingHandles != null) {
            // Make sure handle types for dragging are valid
            for (var ref : draggingHandles) {
                validateDraggingHandleType(keys, ref, draggingHandles);
            }

            // Update aligned
            for (var ref : draggingHandles) {
                // No need to align center
                if (ref.handleIdx() == 0) continue;
                if (ref.keyIdx() < 0 || ref.keyIdx() >= keys.length) {
                    LoggerFactory.getLogger("ReplayLab/ChannelUtils")
                            .error("Keyframe index {} is out of range for array of {} size.", ref.keyIdx, keys.length);
                    continue;
                }

                Keyframe key = keys[ref.keyIdx()];
                if (ref.getType(key) != HandleType.ALIGNED) continue;

                Vector2d sourceVec = ref.getVecRef(key);

                // FIXED: Prevent NaN if dragged exactly to the center
                if (sourceVec.lengthSquared() > 0.000001) {
                    Vector2d direction = new Vector2d(sourceVec).normalize();
                    Vector2d otherVecRef = ref.getOtherVecRef(key);

                    double otherLength = otherVecRef.length();
                    // FIXED: Simplified the math to avoid redundant normalization
                    otherVecRef.set(direction).mul(-otherLength);
                }
            }
        }

        // Re-align aligned handles
        Arrays.sort(keys);
        FCurveHandleCalc.nurbHandleSmoothFcurve(keys);
    }

    private static void validateDraggingHandleType(Keyframe[] keys, LocalHandleRef ref, Collection<LocalHandleRef> dragging) {
        if (ref.handleIdx() == 0) return;
        int otherIdx = ref.handleIdx() == 1 ? 2 : 1;
        // No need to update if we're dragging both handles
        if (dragging.contains(new LocalHandleRef(ref.keyIdx(), otherIdx))) return;

        Keyframe key = keys[ref.keyIdx()]; // FIXED: Use keyIdx, not handleIdx

        HandleType myType = ref.getType(key);
        HandleType nextSelf = requiresMatch(myType) ? HandleType.ALIGNED : HandleType.FREE;
        HandleType nextOther = updateOtherHandleType(nextSelf, ref.getOtherType(key));

        ref.setType(key, nextSelf);
        ref.setOtherType(key, nextOther);
    }

    /**
     * When a handle's type is changed, the opposite handle might need to be updated to remain valid. Given a type
     * change, compute the new type the opposite handle must have.
     *
     * @param nextSelf  The new type this handle is being set to.
     * @param curOther The type the other handle was on.
     * @return The new type for the other handle.
     */
    public static HandleType updateOtherHandleType(HandleType nextSelf, HandleType curOther) {
        if (nextSelf == curOther || requiresMatch(nextSelf)) {
            return nextSelf;
        } else {
            return requiresMatch(curOther) ? HandleType.FREE : curOther;
        }
    }

    public static HandleType getDragHandleType(HandleType curSelf, HandleType curOther) {
        // If the other handle is already of a type that needs a match, "bind" them with aligned.
        // Otherwise, use free to avoid disturbing the other.
        return requiresMatch(curOther) ? HandleType.ALIGNED : HandleType.FREE;
    }

    private boolean requiresMatch(HandleType type) {
        return switch (type) {
            case ALIGNED, AUTO, AUTO_CLAMPED -> true;
            case FREE, VECTOR -> false;
        };
    }
}