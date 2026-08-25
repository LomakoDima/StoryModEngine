package com.dimalab.storymodengine.client.cinematic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Pulls a cutscene camera's desired position in toward whatever solid geometry sits between the
 * viewer and it, so a shot never renders from inside a wall it happens to clip through. Uses the
 * exact same primitive vanilla's own third-person camera already uses for the identical problem
 * (verified against {@code Camera#getMaxZoom} source: {@code Level#clip(ClipContext)}, {@code
 * ClipContext.Block.VISUAL}) — not a new physics/collision system, just one raycast per render
 * frame. Deliberately a single centered ray, not vanilla's own 8-corner cube sweep — a cinematic
 * marker has no collision box of its own to keep clear of a wall, only a viewpoint to keep out of
 * one, so the simpler single ray is the right amount of precision here, not a missing feature.
 *
 * <p>Purely a render-time correction — {@code CameraTrack}'s own keyframes are never touched, so
 * this has zero effect on a shot that never gets near geometry, and never desyncs the deterministic
 * evaluation every other part of {@code cinematic} depends on.
 *
 * <p><b>Only applies within {@link #MAX_CORRECTION_DISTANCE} of the anchor.</b> The vanilla
 * precedent this borrows from ({@code Camera#getMaxZoom}) never needs to check more than ~4 blocks;
 * this class allows a generous multiple of that for legitimate near-player orbit shots, but beyond
 * it the ray is skipped entirely rather than cast. Two real bugs come from *not* capping this: (1) a
 * recorded flythrough (see {@code CameraRecorderCommands}) played back via {@code
 * CutscenePlayJsonCommand} never teleports the viewing player to the path — they can end up hundreds
 * of blocks from the camera, so every frame cast a ray across mostly-unloaded terrain; (2) unloaded
 * chunks read as empty (no hit), so as the client streams chunks in along that ray, the *same* frame
 * position flips between "clear" and "blocked by a hill that just loaded in," snapping the camera
 * toward the anchor and back — the intermittent, chunk-loading-correlated jitter this fixes. A
 * keyframed path far from the anchor was never what this correction was for in the first place: it
 * exists to keep a *near* shot out of a wall, not to re-route a distant flythrough around terrain.
 */
final class CameraCollision {

    /**
     * Blocks. A generous multiple of vanilla's own ~4-block third-person max zoom (the precedent
     * this whole mechanism borrows from) — enough headroom for a deliberately pulled-back orbit shot,
     * while still excluding the long-distance rays a flythrough's anchor-to-camera distance would
     * otherwise produce (see the class doc for why those rays are actively harmful, not just wasted
     * work).
     */
    private static final double MAX_CORRECTION_DISTANCE = 16.0;
    private static final double MAX_CORRECTION_DISTANCE_SQR = MAX_CORRECTION_DISTANCE * MAX_CORRECTION_DISTANCE;

    private CameraCollision() {
    }

    /** {@code desired}, or the point closest to {@code anchor} along the way if something solid is in between and {@code desired} is within {@link #MAX_CORRECTION_DISTANCE}. */
    static Vector3f correct(Level level, Player anchor, Vector3f desired) {
        if (level == null || anchor == null) {
            return desired;
        }
        Vec3 from = anchor.getEyePosition();
        Vec3 to = new Vec3(desired.x, desired.y, desired.z);
        double distanceSqr = from.distanceToSqr(to);
        if (distanceSqr < 1.0E-4 || distanceSqr > MAX_CORRECTION_DISTANCE_SQR) {
            return desired;
        }
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, anchor));
        if (hit.getType() == HitResult.Type.MISS) {
            return desired;
        }
        Vec3 hitPos = hit.getLocation();
        return new Vector3f((float) hitPos.x, (float) hitPos.y, (float) hitPos.z);
    }
}
