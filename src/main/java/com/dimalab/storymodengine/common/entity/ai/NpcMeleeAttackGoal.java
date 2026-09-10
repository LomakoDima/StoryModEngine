package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * {@code MeleeAttackGoal}, with two additions over vanilla's own:
 *
 * <p>Starting it also reports the takeover: this goal legitimately claims navigation away from an
 * in-flight scripted {@code npc_move_to} (combat should win over a walk order, unlike {@link
 * NpcRandomStrollGoal}'s wandering, which is blocked outright instead) but previously did so
 * invisibly — an {@code NpcMoveToTask} awaiting that move had no signal it had been superseded and
 * would eventually report a misleading {@code Result.FAILURE} ("got stuck") once the fight's own path
 * finished somewhere other than the original target. Bumping {@code NpcEntity#movementGeneration} here
 * makes the existing generation check that task already runs report the honest {@code
 * Result.CANCELLED} instead.
 *
 * <p>{@link #tick()} also toggles real sprinting while chasing from a distance, same hysteresis-band
 * shape {@link NpcFollowGoal} uses and for the same reason: vanilla's own {@code
 * speedModifier}/{@code moveTo} calls stay exactly as MeleeAttackGoal always does them (that field is
 * private in the base class, not overridden here) — {@code setSprinting(true)} alone already applies a
 * real {@code +30%} {@code MOVEMENT_SPEED} boost (vanilla's own {@code SPEED_MODIFIER_SPRINTING},
 * verified against the decompiled source), so a distant chase actually closes faster, not just
 * animates as if it did.
 */
public class NpcMeleeAttackGoal extends MeleeAttackGoal {

    private static final double SPRINT_START_DISTANCE_SQR = 36.0D;
    private static final double SPRINT_STOP_DISTANCE_SQR = 16.0D;

    private final NpcEntity npc;

    public NpcMeleeAttackGoal(NpcEntity npc, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(npc, speedModifier, followingTargetEvenIfNotSeen);
        this.npc = npc;
    }

    @Override
    public void start() {
        super.start();
        npc.noteExternalMovementTakeover();
    }

    @Override
    public void stop() {
        super.stop();
        npc.setSprinting(false);
    }

    @Override
    public void tick() {
        LivingEntity target = npc.getTarget();
        if (target != null) {
            double distanceSqr = npc.distanceToSqr(target);
            if (distanceSqr >= SPRINT_START_DISTANCE_SQR) {
                npc.setSprinting(true);
            } else if (distanceSqr <= SPRINT_STOP_DISTANCE_SQR) {
                npc.setSprinting(false);
            }
        }
        super.tick();
    }
}
