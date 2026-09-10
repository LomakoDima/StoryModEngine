package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Makes an NPC actually watch whoever is near it — and turn to face them when they walk around it.
 *
 * <p>This replaces vanilla's {@code LookAtPlayerGoal}, which is not built for the job. Its usual
 * three-argument constructor sets {@code probability = 0.02}, so the goal only <i>starts</i> on about
 * one tick in fifty and gives the head back to {@code RandomLookAroundGoal} in between. That is right
 * for a cow glancing up at you; for a character it reads as "the NPC never looks at me", which is
 * exactly what it was doing.
 *
 * <p>Two things this adds beyond removing the dice:
 * <ul>
 *   <li><b>It keeps looking.</b> No timer, no re-roll — while someone is in range and alive, the head
 *       tracks them.</li>
 *   <li><b>The body follows.</b> A head can only turn {@link Mob#getMaxHeadYRot()} degrees off the
 *       body; past that vanilla just clamps it, and the body only catches up after the head has been
 *       still for ten ticks. So walking around a standing NPC left it staring rigidly past you. When
 *       the angle gets close to the limit and the NPC isn't walking somewhere, this rotates the body
 *       instead, and the NPC turns to face you.</li>
 * </ul>
 *
 * <p>It never fights movement or combat: the body is only turned while the navigation is idle, and the
 * goal holds {@code LOOK} alone, so a higher-priority attack goal takes the head as usual and the
 * stroll goal is free to run underneath.
 */
public class NpcLookAtGoal extends Goal {

    /** How much head-room to leave before handing the turn to the body, so the head never sits pinned at its limit. */
    private static final float HEAD_LIMIT_MARGIN_DEGREES = 15.0F;
    private static final float BODY_TURN_DEGREES_PER_TICK = 6.0F;

    private final Mob mob;
    private final float range;
    private LivingEntity target;

    public NpcLookAtGoal(Mob mob, float range) {
        this.mob = mob;
        this.range = range;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (hasScriptedOverride()) {
            return true;
        }
        // Whatever it is fighting wins; otherwise the closest living thing it can actually see — a
        // player still wins whenever one is nearest, since Player is itself a LivingEntity, but a
        // nearby mob is no longer invisible to it just because it isn't a player.
        LivingEntity combatTarget = mob.getTarget();
        if (combatTarget != null && combatTarget.isAlive()) {
            target = combatTarget;
            return true;
        }
        LivingEntity nearest = findNearestVisibleLiving();
        if (nearest == null) {
            return false;
        }
        target = nearest;
        return true;
    }

    /** Same nearest-pick pattern as {@code EntitySearch.findNearest}, scoped to any living entity instead of one specific type. */
    private LivingEntity findNearestVisibleLiving() {
        AABB box = mob.getBoundingBox().inflate(range);
        double rangeSq = (double) range * range;
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != mob && e.isAlive() && mob.distanceToSqr(e) <= rangeSq)) {
            double distSq = mob.distanceToSqr(candidate);
            if (distSq < nearestDistSq && mob.getSensing().hasLineOfSight(candidate)) {
                nearest = candidate;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    /**
     * Deliberately does not re-check line of sight: once the NPC has noticed someone, a pillar passing
     * between them shouldn't make it instantly forget and start looking around at random.
     */
    @Override
    public boolean canContinueToUse() {
        if (hasScriptedOverride()) {
            return true;
        }
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= range * range;
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean hasScriptedOverride() {
        return mob instanceof NpcEntity npc && (npc.lookOverridePos() != null || npc.lookOverridePlayer() != null);
    }

    /**
     * {@code npc_look_at_pos}/{@code npc_look_at_player} (see {@code NpcScriptCommands}) — checked
     * ahead of the default nearest-player behavior below. A stale player override (they logged off)
     * degrades to the default behavior for this tick rather than freezing the head in place; it isn't
     * cleared outright, since they may simply be temporarily out of range, not gone for good — only
     * {@code npc_stop_look} actually clears it.
     */
    @Override
    public void tick() {
        if (mob instanceof NpcEntity npc) {
            Vec3 overridePos = npc.lookOverridePos();
            if (overridePos != null) {
                mob.getLookControl().setLookAt(overridePos.x, overridePos.y, overridePos.z);
                return;
            }
            UUID overridePlayerId = npc.lookOverridePlayer();
            if (overridePlayerId != null) {
                Player overridePlayer = mob.level().getPlayerByUUID(overridePlayerId);
                if (overridePlayer != null) {
                    mob.getLookControl().setLookAt(overridePlayer.getX(), overridePlayer.getEyeY(), overridePlayer.getZ());
                    return;
                }
            }
        }
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        turnBodyIfHeadCannotReach();
    }

    private void turnBodyIfHeadCannotReach() {
        // While walking, the move control owns the body's facing — overriding it here is what would
        // make the NPC stride off sideways.
        if (!mob.getNavigation().isDone()) {
            return;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        if (dx * dx + dz * dz < 1.0e-4) {
            return;
        }
        float desired = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
        float needed = Math.abs(Mth.degreesDifference(mob.yBodyRot, desired));
        if (needed <= mob.getMaxHeadYRot() - HEAD_LIMIT_MARGIN_DEGREES) {
            return;
        }
        float turned = Mth.approachDegrees(mob.yBodyRot, desired, BODY_TURN_DEGREES_PER_TICK);
        mob.yBodyRot = turned;
        mob.setYRot(turned);
    }
}
