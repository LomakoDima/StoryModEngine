package com.dimalab.storymodengine.common.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/**
 * A {@link LookControl} that actually reaches a steep look-up/look-down angle, instead of being
 * permanently pinned at vanilla's default 40°.
 *
 * <p>Verified directly against the decompiled source: base {@code LookControl.tick()} calls {@code
 * resetXRotOnTick()} (returns {@code true}, never overridden by anything in vanilla's own hierarchy)
 * which does {@code mob.setXRot(0.0F)} <b>every single tick</b>, before re-aiming. Because the pitch
 * recompute always restarts from zero, {@code rotateTowards(0, wanted, xMaxRotAngle)} can never produce
 * more than {@code xMaxRotAngle} — {@link Mob#getMaxHeadXRot()}, 40 by default — no matter how many
 * ticks pass: there is no accumulation to "catch up," unlike yaw ({@code yHeadRot}), which is never
 * reset and so climbs smoothly toward {@link Mob#getMaxHeadYRot()} over several ticks via {@code
 * clampHeadRotationToBody}. That asymmetry is exactly "turning the head sideways works, tilting it up
 * or down does not": a player standing on a block above or below at anything closer than a few blocks
 * routinely needs 50-70° of pitch, which vanilla's mechanism can never reach.
 *
 * <p>Confirmed this isn't a vanilla quirk to just live with: HollowEngine's own look-at-target code
 * (its {@code AISystems.handleLookAt}/{@code NpcSteering.faceTowards}, not its {@code LookControl} at
 * all) bypasses this exact mechanism and writes {@code xRot} directly, clamped to a flat ±90° with no
 * per-tick reset. This does the same thing without abandoning {@code LookControl} outright — {@link
 * #tick()} is vanilla's own body, kept for yaw, with {@link #resetXRotOnTick()} disabled so pitch is
 * free to accumulate the same way yaw already does, and a real absolute clamp ({@link
 * #MAX_HEAD_PITCH_DEGREES}) added afterward so it still can't creep past a human-plausible range over
 * time — a flat 70° rather than HollowEngine's 90, since every case this needs to cover (a player one
 * or two blocks above or below, even at melee range) sits well under that, and a dead vertical stare
 * reads worse than a dead horizontal one reads as a small shortfall.
 */
public class SmoothLookControl extends LookControl {

    private static final float MAX_HEAD_PITCH_DEGREES = 70.0F;

    public SmoothLookControl(Mob mob) {
        super(mob);
    }

    /** The one change from vanilla: stop restarting pitch from zero every tick — see the class doc. */
    @Override
    protected boolean resetXRotOnTick() {
        return false;
    }

    /**
     * Vanilla's own {@code tick()} body (yaw branch untouched), except the pitch branch clamps its
     * result to {@link #MAX_HEAD_PITCH_DEGREES} — needed now that {@link #resetXRotOnTick()} no longer
     * provides an (accidental, too-tight) ceiling of its own.
     */
    @Override
    public void tick() {
        if (this.resetXRotOnTick()) {
            this.mob.setXRot(0.0F);
        }
        if (this.lookAtCooldown > 0) {
            --this.lookAtCooldown;
            this.getYRotD().ifPresent(target -> this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, target, this.yMaxRotSpeed));
            this.getXRotD().ifPresent(target -> {
                float pitch = this.rotateTowards(this.mob.getXRot(), target, this.xMaxRotAngle);
                this.mob.setXRot(Mth.clamp(pitch, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES));
            });
        } else {
            this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
        }
        this.clampHeadRotationToBody();
    }
}
