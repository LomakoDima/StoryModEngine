package com.dimalab.storymodengine.client.model.animator.expr;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code query.*}/{@code q.*} namespace — read-only numeric/boolean accessors mirroring
 * HollowEngine's own query table, mapped onto this project's actual (verified, not assumed) vanilla
 * {@link Entity}/{@link LivingEntity} getters and fields. Booleans read as {@code 1f}/{@code 0f}.
 *
 * <p>Built once into a static {@link Map}; entity-derived entries fall back to a same-named
 * {@link AnimEvalContext#variables} value when {@link AnimEvalContext#entity} is null (a preview with
 * no live entity), exactly like HE's {@code entityFloat}/{@code livingFloat} helpers.
 */
public final class QueryTable {

    public static final Map<String, QueryAccessor> ENTRIES = build();

    private QueryTable() {
    }

    @FunctionalInterface
    private interface EntityFloat {
        float apply(Entity entity);
    }

    @FunctionalInterface
    private interface LivingFloat {
        float apply(LivingEntity entity);
    }

    @FunctionalInterface
    private interface MobFloat {
        float apply(Mob mob);
    }

    private static Map<String, QueryAccessor> build() {
        Map<String, QueryAccessor> t = new HashMap<>();

        context(t, "partial_tick", c -> c.partialTick);
        context(t, "game_time", c -> c.gameTime);
        context(t, "life_time", c -> c.time);
        context(t, "age", c -> c.time);
        context(t, "anim_time", c -> c.time);
        context(t, "layer_time", c -> c.layerTime);
        context(t, "layer_age", c -> c.layerAge);
        context(t, "layer_weight", c -> c.layerWeight);
        context(t, "state_time", c -> c.stateTime);
        context(t, "horizontal_speed", c -> c.horizontalSpeed);
        context(t, "local_forward_speed", c -> c.localForwardSpeed);
        context(t, "local_side_speed", c -> c.localSideSpeed);
        context(t, "signed_horizontal_speed", c -> c.signedHorizontalSpeed);
        context(t, "movement_animation_speed", c -> c.signedHorizontalSpeed);
        context(t, "ground_speed", c -> c.signedHorizontalSpeed);
        context(t, "head_y_rotation", c -> c.headYaw);
        context(t, "head_x_rotation", c -> c.headPitch);
        context(t, "body_y_rotation", c -> c.bodyYaw);
        context(t, "head_body_y_delta", c -> c.headBodyYawDelta);

        entity(t, "entity_id", e -> (float) e.getId());
        entity(t, "is_alive", e -> bool(e.isAlive()));
        entity(t, "is_on_ground", e -> bool(e.onGround()));
        entity(t, "velocity_x", e -> (float) e.getDeltaMovement().x);
        entity(t, "velocity_y", e -> (float) e.getDeltaMovement().y);
        entity(t, "velocity_z", e -> (float) e.getDeltaMovement().z);
        entity(t, "fall_distance", e -> e.fallDistance);
        entity(t, "is_in_water", e -> bool(e.isInWater()));
        entity(t, "is_under_water", e -> bool(e.isUnderWater()));
        entity(t, "is_in_lava", e -> bool(e.isInLava()));
        entity(t, "is_on_fire", e -> bool(e.isOnFire()));
        entity(t, "remaining_fire_ticks", e -> (float) e.getRemainingFireTicks());
        entity(t, "is_crouching", e -> bool(e.isCrouching()));
        entity(t, "is_sprinting", e -> bool(e.isSprinting()));
        entity(t, "is_swimming", e -> bool(e.isSwimming()));
        entity(t, "is_invisible", e -> bool(e.isInvisible()));
        entity(t, "is_passenger", e -> bool(e.isPassenger()));
        entity(t, "is_vehicle", e -> bool(e.isVehicle()));
        entity(t, "is_no_gravity", e -> bool(e.isNoGravity()));
        entity(t, "is_in_wall", e -> bool(e.isInWall()));
        entity(t, "is_shift_key_down", e -> bool(e.isShiftKeyDown()));
        entity(t, "is_sneaking", e -> bool(e.isShiftKeyDown()));
        entity(t, "is_visually_swimming", e -> bool(e.isVisuallySwimming()));
        entity(t, "is_visually_crawling", e -> bool(e.isVisuallyCrawling()));
        entity(t, "is_glowing", e -> bool(e.isCurrentlyGlowing()));
        entity(t, "eye_height", Entity::getEyeHeight);
        entity(t, "bbox_width", Entity::getBbWidth);
        entity(t, "bbox_height", Entity::getBbHeight);
        entity(t, "horizontal_collision", e -> bool(e.horizontalCollision));
        entity(t, "vertical_collision", e -> bool(e.verticalCollision));
        entity(t, "vertical_collision_below", e -> bool(e.verticalCollisionBelow));
        entity(t, "invulnerable_time", e -> (float) e.invulnerableTime);
        entity(t, "has_custom_name", e -> bool(e.hasCustomName()));
        entity(t, "move_dist", e -> e.walkDist);
        entity(t, "fly_dist", e -> e.flyDist);
        entity(t, "is_spectator", e -> bool(e.isSpectator()));
        entity(t, "is_silent", e -> bool(e.isSilent()));
        entity(t, "is_freezing", e -> bool(e.isFreezing()));
        entity(t, "is_invulnerable", e -> bool(e.isInvulnerable()));
        entity(t, "is_attackable", e -> bool(e.isAttackable()));
        entity(t, "is_effective_ai", e -> bool(e.isEffectiveAi()));
        entity(t, "eye_y", e -> (float) e.getEyeY());
        entity(t, "max_fall_distance", e -> (float) e.getMaxFallDistance());
        entity(t, "is_custom_name_visible", e -> bool(e.isCustomNameVisible()));

        living(t, "hurt_time", e -> (float) e.hurtTime);
        living(t, "hurt_duration", e -> (float) e.hurtDuration);
        living(t, "health", LivingEntity::getHealth);
        living(t, "max_health", LivingEntity::getMaxHealth);
        living(t, "health_ratio", e -> e.getHealth() / Math.max(1f, e.getMaxHealth()));
        living(t, "is_dead_or_dying", e -> bool(e.isDeadOrDying()));
        living(t, "death_time", e -> (float) e.deathTime);
        living(t, "death_progress", e -> (float) e.deathTime / LivingEntity.DEATH_DURATION);
        living(t, "armor_value", e -> (float) e.getArmorValue());
        living(t, "absorption_amount", LivingEntity::getAbsorptionAmount);
        living(t, "is_using_item", e -> bool(e.isUsingItem()));
        living(t, "use_item_remaining_ticks", e -> (float) e.getUseItemRemainingTicks());
        living(t, "ticks_using_item", e -> (float) e.getTicksUsingItem());
        living(t, "is_blocking", e -> bool(e.isBlocking()));
        living(t, "swing_time", e -> e.attackAnim);
        living(t, "is_swinging", e -> bool(e.attackAnim > 0f));
        living(t, "is_fall_flying", e -> bool(e.isFallFlying()));
        living(t, "is_climbing", e -> bool(e.onClimbable()));
        living(t, "is_sleeping", e -> bool(e.isSleeping()));
        living(t, "ticks_frozen", e -> (float) e.getTicksFrozen());
        living(t, "percent_frozen", LivingEntity::getPercentFrozen);
        living(t, "is_fully_frozen", e -> bool(e.isFullyFrozen()));
        living(t, "walk_animation_speed", e -> e.walkAnimation.speed());
        living(t, "walk_animation_position", e -> e.walkAnimation.position());
        living(t, "is_moving", e -> bool(Math.abs(e.getDeltaMovement().horizontalDistance()) > 1.0e-4));
        living(t, "air_supply", e -> (float) e.getAirSupply());
        living(t, "max_air_supply", e -> (float) e.getMaxAirSupply());
        living(t, "is_autospin_attack", e -> bool(e.isAutoSpinAttack()));
        living(t, "speed", LivingEntity::getSpeed);
        living(t, "is_sensitive_to_water", e -> bool(e.isSensitiveToWater()));
        living(t, "is_baby", e -> bool(e.isBaby()));
        living(t, "scale", LivingEntity::getScale);
        living(t, "experience_reward", e -> (float) e.getExperienceReward());
        living(t, "hurt_dir", LivingEntity::getHurtDir);
        living(t, "armor_cover_percentage", LivingEntity::getArmorCoverPercentage);
        living(t, "voice_pitch", LivingEntity::getVoicePitch);
        living(t, "jump_boost_power", LivingEntity::getJumpBoostPower);
        living(t, "is_affected_by_potions", e -> bool(e.isAffectedByPotions()));
        living(t, "fall_flying_ticks", e -> (float) e.getFallFlyingTicks());
        // Ticks since this entity's last AI-relevant action — the same counter vanilla checks against
        // Mob.MAXIMUM_NO_ACTION_TIME/-family constants to decide despawn eligibility for some mobs.
        living(t, "no_action_time", e -> (float) e.getNoActionTime());

        // Mob, not LivingEntity — every query below is declared on Mob, which LivingFloat's own
        // signature (fixed to LivingEntity) can't reach directly. Every entity QueryTable actually
        // evaluates against at runtime (context.entity, set every frame in GltfModelLayer.render) is
        // an NpcEntity, a Mob subclass, so the instanceof below is always true in practice — same
        // reasoning is_aggressive/has_target already relied on before this helper existed.
        // Genuinely synced — DATA_MOB_FLAGS_ID, the same flag that gives a vanilla zombie its
        // raised-arms pose — though unsuitable alone for gating a steady facial expression; see
        // has_target's own doc, right below, for why.
        mob(t, "is_aggressive", m -> bool(m.isAggressive()));
        // NpcEntity, not Mob — Mob.getTarget() != null looked right but Mob.target is a plain field,
        // never SynchedEntityData (verified against source), so it's always null on the client no
        // matter what the server is doing. isAggressive() (above) looked like the fix — it genuinely
        // is synced — but MeleeAttackGoal.canContinueToUse(), with followingTargetEvenIfNotSeen=false
        // (what this engine's NPCs use), returns !navigation.isDone(), which goes false the instant
        // the NPC arrives within melee range and stops moving; the goal then stops (clearing
        // aggressive) and only restarts on its own ~1-second canUse() cooldown, so aggressive itself
        // flickers throughout the stationary part of a fight, not just between swings. NpcEntity#setTarget
        // mirrors the real target into its own dedicated synced field specifically to sidestep that
        // goal-internal churn — see its own doc for the full chain.
        living(t, "has_target", e -> bool(e instanceof NpcEntity npc && npc.hasTarget()));
        // Same synced-boolean shape as has_target right above — set by NpcDestroyBlockGoal only once
        // the NPC is actually mid-break (not merely walking toward the block), see its own doc.
        living(t, "is_breaking_block", e -> bool(e instanceof NpcEntity npc && npc.isBreakingBlock()));
        mob(t, "is_leashed", m -> bool(m.isLeashed()));
        mob(t, "is_no_ai", m -> bool(m.isNoAi()));
        mob(t, "is_left_handed", m -> bool(m.isLeftHanded()));
        mob(t, "is_persistence_required", m -> bool(m.isPersistenceRequired()));
        mob(t, "has_restriction", m -> bool(m.hasRestriction()));
        mob(t, "restrict_radius", Mob::getRestrictRadius);
        mob(t, "is_within_restriction", m -> bool(m.isWithinRestriction()));
        // Not implementable against 1.20.1 vanilla, deliberately omitted rather than left unexplained:
        // "max_absorption" has no vanilla concept of a maximum — getAbsorptionAmount() is just
        // whatever an effect set it to, with no ceiling to read; "no_jump_delay" (LivingEntity's own
        // jump-cooldown counter) is a private field with no accessor.

        return Map.copyOf(t);
    }

    private static float bool(boolean value) {
        return value ? 1f : 0f;
    }

    private static void context(Map<String, QueryAccessor> t, String name, QueryAccessor accessor) {
        t.put(name, accessor);
    }

    private static void entity(Map<String, QueryAccessor> t, String name, EntityFloat accessor) {
        t.put(name, ctx -> ctx.entity != null ? accessor.apply(ctx.entity) : ctx.override(name));
    }

    private static void living(Map<String, QueryAccessor> t, String name, LivingFloat accessor) {
        t.put(name, ctx -> ctx.entity != null ? accessor.apply(ctx.entity) : ctx.override(name));
    }

    private static void mob(Map<String, QueryAccessor> t, String name, MobFloat accessor) {
        t.put(name, ctx -> ctx.entity instanceof Mob m ? accessor.apply(m) : ctx.override(name));
    }
}
