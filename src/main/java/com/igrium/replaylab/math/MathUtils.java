package com.igrium.replaylab.math;

import lombok.experimental.UtilityClass;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;

@UtilityClass
public class MathUtils {
    // Vibe-coded because I don't understand the math

    /**
     * Convert a quaternion into an euler suited to put into entities. (the default toEuler breaks when we discard roll)
     * @param rot Quaternion to use.
     * @return Vector with the resulting pitch, yaw, and roll in degrees.
     * @apiNote While roll isn't used in entities, it should still be valid.
     */
    public static Vector3f entityRot(Quaternionfc rot) {
        // Vibe-coded cause I don't get the math. Bite me.
        Vector3f forward = rot.transform(new Vector3f(0, 0, 1));

        float pitch = (float) Math.toDegrees(Math.atan2(-forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z)));
        float yaw = (float) Math.toDegrees(Math.atan2(forward.x, forward.z));

        Quaternionf noRoll = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch));
        float roll = noRoll.conjugate(new Quaternionf()).mul(rot).getEulerAnglesXYZ(new Vector3f()).z; // residual is pure roll
        roll = (float) Math.toDegrees(roll);

        return new Vector3f(pitch, yaw, roll);
    }
}
