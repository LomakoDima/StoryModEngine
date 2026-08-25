package com.dimalab.storymodengine.api.math;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The angle operations {@code Mth} doesn't already cover — wrapping, shortest-path difference, and
 * degree conversion are all real {@code Mth} methods ({@code wrapDegrees}, {@code rotLerp},
 * {@code degreesDifference}, {@code DEG_TO_RAD}/{@code RAD_TO_DEG}) and this class doesn't repeat
 * them; use {@code Mth} directly for those. What's missing from both {@code Mth} and JOML is a
 * conversion between Minecraft's own yaw/pitch convention and a {@code Quaternionf}, which this
 * provides.
 *
 * <p>Minecraft's yaw is degrees clockwise from south (+Z), pitch is degrees below the horizon
 * (positive = looking down) — see {@code Entity.getViewVector}/{@code Camera.setRotation}, which
 * this mirrors exactly (rotate by yaw around Y, then by pitch around X) so a {@code Quaternionf}
 * built here matches what the vanilla camera/entity rotation would produce.
 */
public final class Angles {

    private Angles() {
    }

    /** Builds a rotation from Minecraft yaw/pitch (degrees), roll assumed zero. */
    public static Quaternionf fromYawPitch(float yawDegrees, float pitchDegrees) {
        return fromYawPitchRoll(yawDegrees, pitchDegrees, 0f);
    }

    /** Builds a rotation from Minecraft yaw/pitch/roll (degrees) — same axis order as the vanilla camera. */
    public static Quaternionf fromYawPitchRoll(float yawDegrees, float pitchDegrees, float rollDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateZ((float) Math.toRadians(rollDegrees));
    }

    /**
     * Builds a rotation that looks from {@code from} toward {@code to}, roll assumed zero — the
     * missing counterpart to {@link #fromYawPitch}/{@link #toYawPitch} for when a direction is
     * known as two points rather than an angle (e.g. a camera aimed at a moving target). Returns
     * identity if the two points coincide, rather than dividing by a zero-length direction.
     */
    public static Quaternionf lookAt(Vector3f from, Vector3f to) {
        Vector3f direction = new Vector3f(to).sub(from);
        if (direction.lengthSquared() < 1e-8f) {
            return new Quaternionf();
        }
        direction.normalize();
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-direction.y(), -1f, 1f)));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
        return fromYawPitch(yaw, pitch);
    }

    /** Recovers a Minecraft-style {@code [yaw, pitch]} (degrees) from a rotation with zero roll. */
    public static float[] toYawPitch(Quaternionf rotation) {
        Vec3Direction forward = Vec3Direction.rotate(0, 0, 1, rotation);
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-forward.y(), -1f, 1f)));
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        return new float[]{yaw, pitch};
    }

    /** Tiny local helper so this file doesn't need a JOML {@code Vector3f} allocation just to rotate one axis. */
    private record Vec3Direction(float x, float y, float z) {
        static Vec3Direction rotate(float x, float y, float z, Quaternionf q) {
            org.joml.Vector3f v = q.transform(new org.joml.Vector3f(x, y, z));
            return new Vec3Direction(v.x, v.y, v.z);
        }
    }
}
