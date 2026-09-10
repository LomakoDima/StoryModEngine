package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * {@code npc_follow_player} (see {@code NpcScriptCommands}) — repaths toward {@link
 * NpcEntity#followTarget()} every {@link #REPATH_INTERVAL_TICKS} ticks (or {@link
 * #CLOSE_REPATH_INTERVAL_TICKS} once close — see {@link #tick()}), the same "don't re-path every
 * single tick" cadence {@code WaterAvoidingRandomStrollGoal} and friends already use. Never removes
 * itself and never errors if the followed player logs off or leaves the level — it simply stops
 * moving until {@code npc_stop_move} clears the target (or a valid one reappears), matching this
 * codebase's "a bad script call costs that call, not the story" discipline.
 */
public final class NpcFollowGoal extends Goal {

    private static final int REPATH_INTERVAL_TICKS = 20;
    /**
     * {@code PathNavigation.createPath} (verified against source) reuses the existing path object
     * outright whenever the target's block hasn't changed since the last call — a repath only ever
     * costs a real A* search when the player actually crosses into a new block, which calling this
     * more often doesn't change. What the 20-tick interval was actually bounding is how stale the
     * *path itself* gets while a moving player keeps crossing block boundaries in between: the mob
     * walks toward wherever the player was up to a full second ago, which reads as approaching at a
     * slight, wandering angle rather than straight at them — most visible exactly when the player is
     * close, since that's when a small stale offset is large relative to the remaining distance. This
     * denser interval only applies within {@link #CLOSE_REPATH_DISTANCE_SQR}, so the far-away case
     * keeps the original cadence and its A* cost unchanged.
     */
    private static final int CLOSE_REPATH_INTERVAL_TICKS = 5;
    private static final double CLOSE_REPATH_DISTANCE_SQR = 36.0D;
    /** Stop closing the distance once this close — otherwise the NPC keeps shoving into the followed player. */
    private static final double STOP_DISTANCE_SQR = 4.0D;
    /**
     * {@code LivingEntity.setSprinting(boolean)} already adds/removes vanilla's own real {@code
     * SPEED_MODIFIER_SPRINTING} (+30%, {@code MULTIPLY_TOTAL} — verified against the decompiled
     * source) on {@code Attributes.MOVEMENT_SPEED} for any {@code LivingEntity}, not just a real
     * player — so toggling this is a genuine speed change, not just an animation flag, and needs no
     * hand-rolled modifier of our own. Two different thresholds (start higher than stop) so the toggle
     * doesn't flicker right at one fixed distance the way a single threshold would once the NPC's own
     * speed change starts affecting how fast that distance itself closes.
     */
    private static final double SPRINT_START_DISTANCE_SQR = 36.0D;
    private static final double SPRINT_STOP_DISTANCE_SQR = 16.0D;

    private final NpcEntity npc;
    private int repathCooldown;

    public NpcFollowGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return npc.followTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.followTarget() != null;
    }

    @Override
    public void stop() {
        npc.setSprinting(false);
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (repathCooldown-- > 0) {
            return;
        }

        UUID targetId = npc.followTarget();
        Player player = targetId == null ? null : npc.level().getPlayerByUUID(targetId);
        if (player == null || !player.isAlive()) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            npc.setSprinting(false);
            npc.getNavigation().stop();
            return;
        }
        double distanceSqr = npc.distanceToSqr(player);
        repathCooldown = distanceSqr <= CLOSE_REPATH_DISTANCE_SQR ? CLOSE_REPATH_INTERVAL_TICKS : REPATH_INTERVAL_TICKS;
        if (distanceSqr <= STOP_DISTANCE_SQR) {
            npc.setSprinting(false);
            npc.getNavigation().stop();
            return;
        }
        if (distanceSqr >= SPRINT_START_DISTANCE_SQR) {
            npc.setSprinting(true);
        } else if (distanceSqr <= SPRINT_STOP_DISTANCE_SQR) {
            npc.setSprinting(false);
        }
        npc.getNavigation().moveTo(player, 1.0D);
    }
}
