package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives {@link FollowBehavior}/{@link LookAtBehavior} for <b>any</b> {@link Mob} — a plain vanilla
 * zombie, a villager, anything — not just {@code NpcEntity}. {@code Mob.goalSelector}/{@code
 * targetSelector} are {@code protected} (verified against the decompiled source): nothing outside a
 * {@code Mob} subclass's own code can add a real {@code Goal} to an already-spawned, arbitrary mob from
 * an external command handler. A global per-tick scan sidesteps that entirely — the same architectural
 * answer HollowEngine's own generic AI-component system uses (its {@code AttachmentRegistry.tick(level)}
 * → {@code AIComponentSystems.tickEntity}, confirmed this session against its real source), though HE
 * never actually wired that system to its own scripting layer — this one is wired, end to end, from the
 * first commit.
 *
 * <p>Runs off {@code LivingEvent.LivingTickEvent} rather than a custom level-tick hook: it already fires
 * once per tick per living entity, server and client both, so this only has to gate client-side out.
 * Checking two capabilities on every living entity every tick sounds expensive but isn't — {@link
 * Capabilities#get} is a plain map lookup into data that already exists on the entity (the same cost
 * {@code ModelAttachment} already pays for every entity in the world, in production since earlier this
 * session).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class GenericBehaviorSystem {

    private static final int REPATH_INTERVAL_TICKS = 20;
    private static final int CLOSE_REPATH_INTERVAL_TICKS = 5;
    private static final double CLOSE_REPATH_DISTANCE_SQR = 36.0D;
    /** Stop closing the distance once this close — otherwise the mob keeps shoving into the followed player. Mirrors {@code NpcFollowGoal}'s own tuned value. */
    private static final double STOP_DISTANCE_SQR = 4.0D;

    private GenericBehaviorSystem() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        tickFollow(mob);
        tickLookAt(mob);
    }

    private static void tickFollow(Mob mob) {
        FollowBehavior follow = Capabilities.get(mob, GenericBehaviorCapabilities.FOLLOW);
        if (follow == null || follow.targetId.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) mob.level();
        Entity target = level.getEntity(follow.targetId.get());
        if (target == null || !target.isAlive()) {
            mob.getNavigation().stop();
            return;
        }
        if (follow.repathCooldown-- > 0) {
            return;
        }
        double distanceSqr = mob.distanceToSqr(target);
        follow.repathCooldown = distanceSqr <= CLOSE_REPATH_DISTANCE_SQR ? CLOSE_REPATH_INTERVAL_TICKS : REPATH_INTERVAL_TICKS;
        if (distanceSqr <= STOP_DISTANCE_SQR) {
            mob.getNavigation().stop();
            return;
        }
        PathNavigation navigation = mob.getNavigation();
        navigation.moveTo(target.getX(), target.getY(), target.getZ(), 1.0D);
    }

    /** Same nearest-pick pattern {@code NpcLookAtGoal.findNearestVisibleLiving()} already uses — duplicated rather than shared, since that method lives inside a {@code Goal} this system can't attach to an arbitrary mob anyway (see this class's own doc). */
    private static void tickLookAt(Mob mob) {
        LookAtBehavior lookAt = Capabilities.get(mob, GenericBehaviorCapabilities.LOOK_AT);
        if (lookAt == null || !lookAt.enabled) {
            return;
        }
        AABB box = mob.getBoundingBox().inflate(lookAt.range);
        double rangeSq = (double) lookAt.range * lookAt.range;
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
        if (nearest != null) {
            mob.getLookControl().setLookAt(nearest.getX(), nearest.getEyeY(), nearest.getZ());
        }
    }
}
