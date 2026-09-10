package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;

/**
 * A {@link MoveControl} that turns the body smoothly instead of snapping it at every waypoint.
 *
 * <p>Vanilla's own {@code MoveControl} re-aims the mob at its next path node with {@code
 * rotlerp(yRot, target, 90.0F)} — up to <b>90° in a single tick</b> — and does so no matter how small
 * the correction is. Because a path is a chain of block-grid nodes, a mob walking toward you is
 * constantly being re-aimed a few degrees left, then a few degrees right, and at 90°/tick each of
 * those corrections lands instantly. That reads as the mob veering in hard angles rather than walking
 * at you, which is exactly the "zigzag" this fixes.
 *
 * <p>Two changes, both about <i>when</i> and <i>how fast</i> to turn — never about where to go:
 * <ul>
 *   <li>a <b>dead zone</b>: corrections under {@link #TURN_DEAD_ZONE_DEGREES} are ignored outright,
 *       so grid noise stops producing motion at all;</li>
 *   <li>a <b>rate cap</b>: what remains is applied at most {@link #MAX_BODY_TURN_DEGREES} per tick,
 *       so a real change of direction sweeps instead of snapping.</li>
 * </ul>
 *
 * <p><b>The head is deliberately left alone.</b> It belongs to {@code LookControl}, which is what aims
 * it at whatever the mob is looking at; steering the head from here as well would fight the look
 * goals and stop the mob tracking the player.
 */
public class SmoothMoveControl extends MoveControl {

    /** Below this, a correction is grid noise rather than a change of direction. This is what actually kills the jitter. */
    private static final float TURN_DEAD_ZONE_DEGREES = 2.0F;
    /**
     * Vanilla allows 90. This must stay <b>close</b> to it, and an earlier version's 15 was a mistake:
     * {@code Mob.travel} moves the mob along {@code yRot}, so a body that cannot turn fast enough does
     * not merely look late — it physically walks off at an angle to where it is trying to go, which is
     * what "walks crookedly at the player" was. 60 still takes the edge off an instant snap while
     * leaving the lag imperceptible.
     */
    private static final float MAX_BODY_TURN_DEGREES = 60.0F;
    /** Only a genuine reversal slows the mob down; a chase correction must not. */
    private static final float SHARP_TURN_DEGREES = 75.0F;
    private static final float SHARP_TURN_SPEED_FACTOR = 0.5F;
    /**
     * {@code Mob.travel} launches a jump using the body's current facing, not the direction the jump
     * is actually needed in — jumping while badly misaligned sends the mob up and to the side of where
     * it meant to go, not just late, for the same reason the body-turn cap above exists. Below this,
     * the jump waits a tick for the body-turn cap to close the angle further instead of firing anyway.
     */
    private static final float MAX_JUMP_ANGLE_DEGREES = 30.0F;
    /**
     * {@code risesAhead} below used to gate on {@code dx*dx + dz*dz < max(1, bbWidth)} — vanilla's own
     * check (see {@code MoveControl.tick()}), sized for a target one cell away in a cardinal direction
     * (distance ≈ 1, squared ≈ 1). A <b>diagonal</b> adjacent step is ≈1.41 away — squared ≈ 2 — so it
     * never passed that test at all: a mob jumping up and across a corner formed by two blocks (exactly
     * the "jump between two blocks" case) always failed this check and just walked into the corner
     * instead. Checking each axis separately instead of the combined squared distance accepts both a
     * cardinal step (one axis ≈1, the other ≈0) and a diagonal one (both axes ≈1) alike, while a target
     * genuinely several cells away — e.g. a farther lookahead point {@code SmoothGroundNavigation}'s
     * string-pulling picked — still fails on at least one axis, so this stays just as unlikely to jump
     * prematurely toward a distant point as the original check was.
     */
    private static final float MAX_JUMP_TARGET_AXIS_DISTANCE = 1.25F;

    public SmoothMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            super.tick();
            return;
        }
        this.operation = Operation.WAIT;

        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < 2.5000002667774E-7) {
            this.mob.setZza(0.0F);
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float delta = Mth.wrapDegrees(targetYaw - this.mob.getYRot());
        if (Math.abs(delta) >= TURN_DEAD_ZONE_DEGREES) {
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYaw, MAX_BODY_TURN_DEGREES));
        }

        float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        this.mob.setSpeed(Math.abs(delta) > SHARP_TURN_DEGREES ? speed * SHARP_TURN_SPEED_FACTOR : speed);

        // Jump handling, kept equivalent to vanilla's: step up onto something in the way, or over a
        // rise the mob can't simply walk up.
        //
        // `blocked` used to be gated behind `this.mob.getNavigation().canFloat()` — canFloat is purely
        // about whether the pathfinder lets the mob swim/float over water (WalkNodeEvaluator's own
        // water-avoidance flag, verified against NodeEvaluator source), never set true anywhere for
        // NpcEntity, and unrelated to "did I just walk into a solid block." That accidentally made
        // this whole reactive branch dead code for every NPC, leaving only `risesAhead` — which needs
        // the move control's *current* wanted point within about a block horizontally to fire — to
        // catch a rise. Once SmoothGroundNavigation's own string-pulling started aiming several path
        // nodes ahead in a straight line (steerPastNodes), that point is routinely farther than a
        // block away, so `risesAhead` silently stopped firing too on exactly the runs where a shortcut
        // was being taken, and the mob just walked into the block instead of hopping it. Restoring the
        // reactive check (vanilla's own doors/fences exclusion included, since a mob should open a door
        // rather than hop it, and can never clear a fence with a normal jump anyway) gives back a
        // same-tick fallback that doesn't depend on the move target's exact distance.
        BlockPos pos = this.mob.blockPosition();
        BlockState state = this.mob.level().getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(this.mob.level(), pos);
        boolean blocked = !shape.isEmpty()
                && this.mob.getY() + (double) this.mob.getStepHeight() < shape.max(net.minecraft.core.Direction.Axis.Y) + (double) pos.getY()
                && !state.is(BlockTags.DOORS)
                && !state.is(BlockTags.FENCES);
        float maxAxisDistance = Math.max(MAX_JUMP_TARGET_AXIS_DISTANCE, this.mob.getBbWidth());
        boolean risesAhead = dy > (double) this.mob.getStepHeight()
                && Math.abs(dx) <= (double) maxAxisDistance
                && Math.abs(dz) <= (double) maxAxisDistance;
        if ((risesAhead || blocked) && Math.abs(delta) <= MAX_JUMP_ANGLE_DEGREES) {
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }
}
