package com.igrium.replaylab.math;

import lombok.experimental.UtilityClass;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@UtilityClass
public class MathUtils {
    // Vibe-coded because I don't understand the math

    /**
     * Convert a quaternion into an euler suited to put into entities. (the default toEuler breaks when we discard roll)
     *
     * @param rot Quaternion to use.
     * @return Vector with the resulting pitch, yaw, and roll in degrees.
     * @apiNote While roll isn't used in entities, it should still be valid.
     */
    public static Vector3f toEntityRot(Quaternionfc rot) {
        // Vibe-coded cause I don't get the math. Bite me.
        Vector3f forward = rot.transform(new Vector3f(0, 0, 1));

        float pitch = (float) Math.toDegrees(Math.atan2(-forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z)));
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));

        // Negate yaw here — JOML rotateY goes opposite to Minecraft's yaw convention
        Quaternionf noRoll = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));

        float roll = noRoll.conjugate(new Quaternionf()).mul(rot).getEulerAnglesXYZ(new Vector3f()).z;
        roll = (float) Math.toDegrees(roll);

        return new Vector3f(pitch, yaw, roll);
    }

    /**
     * Convert a YXZ euler into an euler suited to put into entities.
     * <p>
     * An entity rotation <em>is</em> a YXZ euler, just in degrees and with the yaw the other way around, so this
     * doesn't decompose anything: angles outside the ranges a quaternion round-trip would snap to (a pitch past
     * ±90, a yaw that's wound around a few times) come out as authored.
     *
     * @param yxzEuler YXZ euler to use, in radians. Applied in the order Y, X, Z.
     * @return Vector with the resulting pitch, yaw, and roll in degrees.
     * @apiNote While roll isn't used in entities, it should still be valid.
     */
    public static Vector3f toEntityRot(Vector3fc yxzEuler) {
        return new Vector3f(
                (float) Math.toDegrees(yxzEuler.x()),
                (float) Math.toDegrees(-yxzEuler.y()),
                (float) Math.toDegrees(yxzEuler.z())
        );
    }

    /**
     * Convert an entity euler into a quaternion. The inverse of {@link #toEntityRot(Quaternionfc)}.
     *
     * @param entityRot Pitch, yaw, and roll in degrees.
     * @return The resulting rotation.
     */
    public static Quaternionf fromEntityRot(Vector3fc entityRot) {
        Vector3f euler = fromEntityRotEuler(entityRot);
        return new Quaternionf().rotateYXZ(euler.y, euler.x, euler.z);
    }

    /**
     * Convert an entity euler into a YXZ euler. The inverse of {@link #toEntityRot(Vector3fc)}.
     *
     * @param entityRot Pitch, yaw, and roll in degrees.
     * @return The resulting YXZ euler, in radians. Applied in the order Y, X, Z.
     */
    public static Vector3f fromEntityRotEuler(Vector3fc entityRot) {
        return new Vector3f(
                (float) Math.toRadians(entityRot.x()),
                (float) Math.toRadians(-entityRot.y()),
                (float) Math.toRadians(entityRot.z())
        );
    }
}
