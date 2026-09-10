package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationExpression;
import com.dimalab.storymodengine.client.model.animator.expr.AnimationVectorExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.dimalab.storymodengine.client.model.animator.AnimationControllerTransitionSpec.ANY_STATE;

/**
 * SME's default humanoid NPC controller — a direct port (reference only, not copied source; see
 * {@code MODEL_SYSTEM_DESIGN.md}) of HollowEngine's own {@code StandardPlayerAnimatorPreset.kt}:
 * a locomotion state machine plus a head/eye look-procedural layer, driving the same
 * {@code player_model.gltf}/{@code player_model_slim.gltf} rig HE itself ships.
 */
public final class StandardPlayerAnimatorPreset {

    public static final String ID = "storymodengine:standard_player";

    private static final Set<String> LOOPED_STATES = Set.of("idle", "walk", "run", "sneak", "levitation", "mining");
    private static final Set<String> CLAMPED_STATES = Set.of("death", "sit", "sleep", "lay");

    private StandardPlayerAnimatorPreset() {
    }

    public static List<AnimatorLayerSpec> create() {
        List<AnimationControllerStateSpec> states = new ArrayList<>();
        // "mining" is a real clip on this rig (player_model.gltf/player_model_slim.gltf both ship it,
        // alongside "chooping"/"digging" — verified by inspecting the model JSON directly), looped
        // while query.is_breaking_block stays true (see NpcDestroyBlockGoal/npc_break_block) instead
        // of reusing the combat "attack" swing, which reads as fighting the air rather than working a
        // block.
        for (String animation : List.of("death", "attack", "mining", "sneak", "run", "levitation", "walk", "idle")) {
            states.add(stateFor(animation));
        }

        AnimatorLayerSpec locomotion = AnimationControllerLayerSpec.of(
                "standard_player:locomotion", states, locomotionTransitions()).withEntryState("idle");

        AnimatorLayerSpec lookProcedural = ProceduralLayerSpec.of("standard_player:look_procedural", List.of(
                        // Pitch (X) sign: this rig's Head bone turns out to rotate opposite of
                        // vanilla's own headPitch convention (positive = look down, confirmed against
                        // LookControl.getXRotD()) — established by live in-game testing, not static
                        // analysis. An earlier version of this line removed the negation here on a
                        // "double negation carried over from dead HumanoidPoser code" theory; after
                        // that shipped, standing a player on a block above vs. below the NPC produced
                        // exactly inverted pitch (looked down at a player above, up at a player below).
                        // That theory was wrong about the cause, but the fix it produced was the
                        // opposite of what this rig actually needs — restoring the negation corrects
                        // the now-confirmed inversion. Yaw ("head_body_y_delta") is untouched, since
                        // both reported symptoms (then and now) were specifically vertical.
                        new ProceduralBoneTransformSpec("Head", null,
                                vector("-query.head_x_rotation * 0.65", "-query.head_body_y_delta * 0.62", "0"), null),
                        // The previous version of this comment reasoned from the eyelid meshes
                        // ("leftUp"/"leftDown") as the limiting "socket" — wrong: LeftEye's children
                        // (the pupil holder AND both eyelids) all ride the same "LeftEye" bone this
                        // translation targets, so they move together and can never clip each other no
                        // matter the magnitude. The real, static boundary is the actual eye socket: a
                        // transparent cutout painted into the head texture itself (player_model.gltf's
                        // embedded "classic" skin, material 0), which does NOT move with this bone.
                        // Extracted that texture and read its alpha channel directly — a clean, sharp
                        // 2x2-pixel hole per eye (x=9-10/13-14, y=12-13 in the 64x64 image, alpha 0 vs
                        // 255 either side, no antialiasing to fudge). Head mesh ("golova") measures
                        // 0.5 world units across its 8-pixel front face, so 1px = 0.0625 units and the
                        // hole is 0.125x0.125. The pupil mesh ("leftLib") measures 0.0625 (X) x 0.125
                        // (Y) — already exactly the hole's own height, so there is zero real vertical
                        // margin (any Y offset starts revealing texture outside the cutout), while X
                        // has a genuine (0.125 - 0.0625) / 2 = 0.03125 units of travel each way before
                        // the pupil's edge reaches the hole's edge. Coefficients below land exactly on
                        // that measured X limit at the yaw clamp's ±18 extreme; Y is kept to a small
                        // fraction of a pixel rather than the full range HE's differently-sized rig can
                        // afford, since this texture's own eye cutout genuinely has none to give.
                        new ProceduralBoneTransformSpec("LeftEye",
                                vector("clamp(query.head_body_y_delta, -18, 18) * 0.0017361", "clamp(query.head_x_rotation, -10, 10) * 0.0002", "0"), null, null),
                        new ProceduralBoneTransformSpec("RightEye",
                                vector("clamp(query.head_body_y_delta, -18, 18) * 0.0017361", "clamp(query.head_x_rotation, -10, 10) * 0.0002", "0"), null, null)))
                .withPriority(20)
                .withBlendMode(LayerBlendMode.ADDITIVE)
                .withMask(BoneMask.of("Head", "BodyUp", "LeftArm", "RightArm", "LeftEye", "RightEye"));

        // A held expression (single-keyframe "face-angry" clip — verified against the gltf: every
        // channel has exactly one keyframe, no actual motion to play), gated by weight rather than a
        // controller state — see AnimatorLayerSpec#weight/SpecLayer#weight, the same mechanism the
        // engine already uses to skip a layer entirely once its expression evaluates to 0. This took
        // three attempts to get a genuinely stable signal for, all documented on QueryTable's own
        // has_target entry: is_swinging pulses with each attack cycle; Mob.getTarget() != null is
        // never synced to the client at all; is_aggressive IS synced but MeleeAttackGoal's own
        // canContinueToUse() flickers it once a second once the NPC stops moving to actually swing.
        // has_target — NpcEntity's own dedicated synced field, set from NpcEntity#setTarget directly —
        // is what's actually stable for the whole engagement. Bone mask resolved from the clip's own
        // channels, not guessed: LeftUp/RightUp (upper eyelids)/LeftBrow/RightBrow/Brows — none of
        // which any locomotion clip or look_procedural's own transforms touch, so ADDITIVE here can't
        // fight anything else today; kept additive and above look_procedural's priority anyway so a
        // future face layer sharing those bones still composes correctly instead of clobbering. Snaps
        // on/off rather than crossfading — fadeIn/fadeOut only smooth a layer's first frame(s) after
        // the model loads and a ONCE-clip's own end, not a weight expression flipping frame to frame;
        // worth revisiting with a small AnimationControllerLayerSpec crossfade if the snap reads poorly.
        AnimatorLayerSpec angryFace = ClipAnimationLayerSpec.of("standard_player:face_angry", "face-angry")
                .withPlayMode(AnimationPlayMode.LOOP)
                .withWeight(AnimationExpression.of("query.is_alive != 0.0 && query.has_target != 0.0"))
                .withPriority(25)
                .withBlendMode(LayerBlendMode.ADDITIVE)
                .withMask(BoneMask.of("LeftUp", "RightUp", "LeftBrow", "RightBrow", "Brows"));

        return List.of(locomotion, lookProcedural, angryFace);
    }

    private static AnimationControllerStateSpec stateFor(String animation) {
        AnimationPlayMode playMode = CLAMPED_STATES.contains(animation) ? AnimationPlayMode.CLAMP_FOREVER
                : LOOPED_STATES.contains(animation) ? AnimationPlayMode.LOOP
                : AnimationPlayMode.ONCE;
        return new AnimationControllerStateSpec(animation, animation, playMode, AnimationExpression.of(stateSpeedExpression(animation)));
    }

    private static String stateSpeedExpression(String animation) {
        return switch (animation) {
            case "walk", "sneak" -> "query.movement_animation_speed / 2.0";
            // A separate, larger divisor from walk/sneak — sharing walk's /2.0 played this clip back
            // too fast once real sprinting was wired up (see NpcFollowGoal/NpcMeleeAttackGoal): a run
            // cycle's own baked stride covers noticeably more ground per loop than a walk cycle does,
            // so the same raw ground-speed number needs a bigger divisor here to land on a natural
            // cadence, on top of which the real +30% sprint speed itself (vanilla's own
            // SPEED_MODIFIER_SPRINTING) was compounding against that same too-small divisor. An
            // empirical starting point, not derived from the clip's own authored timing (no such
            // metadata is available) — worth another look if it still doesn't read as natural.
            case "run" -> "query.movement_animation_speed / 4.0";
            default -> "1";
        };
    }

    private static List<AnimationControllerTransitionSpec> locomotionTransitions() {
        return List.of(
                transition("death", "query.death_progress > 0.0", 100, "0.1"),
                // Above every movement state so a swing reads clearly even mid-stride, below death so
                // a killing blow still wins. query.is_swinging is entity.attackAnim > 0 — vanilla's own
                // swing timer (see QueryTable) — so this exits back to whichever locomotion state
                // applies on its own, the moment the swing timer decays, with no explicit exit rule
                // needed. Neither HollowEngine's own default preset nor SME's had an attack state
                // before this — the attack clip existed on the model but nothing ever selected it.
                transition("attack", "query.is_alive != 0.0 && query.is_swinging != 0.0", 90, "0.08"),
                // Below attack/above sneak: a killing blow or a genuine attack swing still wins, but
                // this otherwise dominates over idle/walk the whole time the NPC is actually breaking
                // a block (which stops navigation itself, so horizontal_speed is already ~0 — without
                // this, is_breaking_block would just lose silently to idle's own condition).
                transition("mining", "query.is_alive != 0.0 && query.is_breaking_block != 0.0", 85, "0.1"),
                transition("sneak", "query.is_alive != 0.0 && query.is_sneaking != 0.0", 80, "0.18"),
                transition("run", "query.is_alive != 0.0 && query.is_sprinting != 0.0 && query.horizontal_speed > 0.02", 70, "0.18"),
                // Was gated to velocity_y > 0.05 — rising only, i.e. a jump's ascent. A fall (off a
                // ledge, knocked back into open air, any velocity_y < 0 while airborne) matched none
                // of the states above and fell through to walk/idle's own conditions, which only look
                // at horizontal_speed — so an NPC falling straight down with little horizontal motion
                // rendered as standing still, mid-air. Airborne is airborne regardless of which way
                // it's currently moving vertically; this clip already covers both directions visually
                // (there's no separate "fall" clip on the rig — verified against the model JSON).
                transition("levitation", "query.is_alive != 0.0 && query.is_on_ground == 0.0", 60, "0.18"),
                transition("walk", "query.is_alive != 0.0 && query.horizontal_speed > 0.02", 50, "0.18"),
                transition("idle", "query.is_alive != 0.0 && query.horizontal_speed <= 0.02", 0, "0.18"));
    }

    private static AnimationControllerTransitionSpec transition(String to, String condition, int priority, String duration) {
        return new AnimationControllerTransitionSpec(ANY_STATE, to, AnimationExpression.of(condition), AnimationExpression.of(duration), priority, null);
    }

    private static AnimationVectorExpression vector(String x, String y, String z) {
        return AnimationVectorExpression.of(x, y, z);
    }
}
