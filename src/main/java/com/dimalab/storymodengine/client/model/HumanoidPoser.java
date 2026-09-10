package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.model.rig.HumanoidBones;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * Procedural animation for a rigged humanoid — walk, idle sway, head aim and an attack swing, driven
 * from entity state rather than from animation clips.
 *
 * <p>This exists because the models this engine targets are typically Blockbench exports: flat part
 * lists with no skin and <b>no animation clips at all</b> (the shipped player model has zero). glTF
 * clips are still fully supported when a model has them — see {@code ModelAnimator} — but a model
 * that has none would otherwise stand frozen.
 *
 * <p><b>Every X and Y rotation here is negated, and that is not a fudge.</b> Vanilla draws entity
 * models inside a {@code scale(-1, -1, 1)} flip, and all of Minecraft's own angle conventions —
 * including the {@code netHeadYaw}/{@code headPitch} handed to a render layer — are expressed in that
 * flipped space. {@code GltfModelLayer} undoes the flip so imported Y-up geometry draws correctly,
 * and conjugating a rotation by that flip inverts it: {@code S·R(θ)·S⁻¹ = R(−θ)} for X and Y, while Z
 * is unchanged because its two negated axes cancel. Verified numerically against vanilla's own
 * transform chain, not reasoned about and hoped for.
 *
 * <p>Without the negation the head turns exactly the wrong way — the player stands on the right and
 * the character looks left — and the limbs walk backwards.
 */
public final class HumanoidPoser {

    /** See the class doc: X and Y are mirrored relative to Minecraft's own angle convention. */
    private static final float AXIS_FLIP = -1.0F;

    private HumanoidPoser() {
    }

    /**
     * @param limbSwing       distance walked, for the stride phase
     * @param limbSwingAmount how much the entity is actually moving, 0 when still
     * @param ageInTicks      free-running clock, for idle sway
     * @param netHeadYaw      head yaw relative to the body, degrees, in Minecraft's convention
     * @param headPitch       head pitch, degrees, in Minecraft's convention
     * @param attackProgress  0..1 while a melee swing plays, 0 otherwise
     */
    public static void pose(ModelInstance instance, float limbSwing, float limbSwingAmount,
                            float ageInTicks, float netHeadYaw, float headPitch, float attackProgress) {
        Map<String, RuntimeNode> bones = instance.nodesByName();

        RuntimeNode head = bones.get(HumanoidBones.HEAD);
        if (head != null) {
            head.rotation()
                    .rotateY(AXIS_FLIP * netHeadYaw * Mth.DEG_TO_RAD)
                    .rotateX(AXIS_FLIP * headPitch * Mth.DEG_TO_RAD);
        }

        // Opposite arms and legs swing together, the standard biped gait — the same phases and
        // amplitudes vanilla's HumanoidModel uses.
        float stride = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        applyPitch(bones.get(HumanoidBones.RIGHT_ARM), -stride * 0.5f);
        applyPitch(bones.get(HumanoidBones.LEFT_ARM), stride * 0.5f);
        applyPitch(bones.get(HumanoidBones.RIGHT_LEG), stride);
        applyPitch(bones.get(HumanoidBones.LEFT_LEG), -stride);

        // Idle sway: a small counter-phase drift on the arms so a standing figure isn't rigid. Roll is
        // the one axis the flip leaves alone, so it takes no correction.
        float idle = Mth.cos(ageInTicks * 0.09f) * 0.05f * (1f - limbSwingAmount);
        applyRoll(bones.get(HumanoidBones.RIGHT_ARM), -idle);
        applyRoll(bones.get(HumanoidBones.LEFT_ARM), idle);

        if (attackProgress > 0f) {
            // Vanilla's own swing curve: fast out, easing back.
            float swing = Mth.sin(Mth.sqrt(attackProgress) * Mth.PI);
            applyPitch(bones.get(HumanoidBones.RIGHT_ARM), -swing * 2.2f);
            RuntimeNode body = bones.get(HumanoidBones.BODY);
            if (body != null) {
                body.rotation().rotateY(AXIS_FLIP * swing * 0.2f);
            }
        }
    }

    private static void applyPitch(RuntimeNode bone, float radians) {
        if (bone != null) {
            bone.rotation().rotateX(AXIS_FLIP * radians);
        }
    }

    /** Roll needs no flip correction — see the class doc. */
    private static void applyRoll(RuntimeNode bone, float radians) {
        if (bone != null) {
            bone.rotation().rotateZ(radians);
        }
    }
}
