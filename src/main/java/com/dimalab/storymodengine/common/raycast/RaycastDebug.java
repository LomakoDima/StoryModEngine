package com.dimalab.storymodengine.common.raycast;

import com.dimalab.storymodengine.client.raycast.RaycastClientDebug;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples a raycast's path into a short line of vanilla particles — no custom renderer, no {@code
 * VertexConsumer}, no {@code RenderType}, just repeated {@code ServerLevel#sendParticles}/{@code
 * ClientLevel#addParticle} calls, exactly the "штатный механизм" the spec calls for. Only ever runs
 * when {@link RaycastQuery#debug} was actually called — {@link RaycastQuery#cast()} never spawns a
 * single particle otherwise, so normal (non-debug) casts allocate nothing here at all.
 *
 * <p>One-shot, not a runtime: every particle for one {@code cast()} is placed synchronously in this
 * one call. No tick loop, no persistent entity, no scheduled follow-up — vanilla particles are
 * already transient, so "draw a ray" is just "place several of them at once."
 *
 * <p>Deliberately mirrors {@code network.context.ClientLevelLookup}'s dist-safety split: this class
 * is loaded on both sides (it's what {@link RaycastQuery} calls directly, and {@code RaycastQuery}
 * itself has no {@code Dist} gate), so its own bytecode must never reference a client-only type.
 * {@code ServerLevel} isn't {@code @OnlyIn}, so that branch is handled directly here; the client
 * branch is delegated to {@link RaycastClientDebug}, the only class in this package that references
 * {@code ClientLevel}/{@code Minecraft}. Forge's {@code RuntimeDistCleaner} checks a class's own
 * bytecode for {@code @OnlyIn}-mismatched references at class-*load* time, not per executed branch —
 * so isolating that reference into its own, only-loaded-when-actually-called class is what keeps a
 * dedicated server from crashing the moment this class loads, even though the client branch would
 * never execute there anyway.
 */
final class RaycastDebug {

    /** Hard cap regardless of distance/spacing — see the class doc's performance note (spec §13). */
    private static final int MAX_PARTICLES = 96;
    private static final double MIN_SPACING = 0.1;

    private RaycastDebug() {
    }

    static void visualize(Level level, Vec3 start, RaycastResult result, ParticleOptions particle, double spacing) {
        Vec3 end = result.position();
        List<Vec3> points = samplePoints(start, end, Math.max(spacing, MIN_SPACING));

        if (level instanceof ServerLevel serverLevel) {
            for (Vec3 point : points) {
                serverLevel.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (result.isHit()) {
                // A denser little burst at the hit point is what tells a hit apart from a miss —
                // deliberately not a second particle type, so `.debug(particle)` stays a one-argument decision.
                serverLevel.sendParticles(particle, end.x, end.y, end.z, 8, 0.05, 0.05, 0.05, 0.0);
            }
        } else {
            RaycastClientDebug.spawn(level, points, particle, result.isHit() ? end : null);
        }
    }

    private static List<Vec3> samplePoints(Vec3 start, Vec3 end, double spacing) {
        double length = start.distanceTo(end);
        if (length < 1.0e-6) {
            return List.of(start);
        }
        int count = Math.min(MAX_PARTICLES, Math.max(1, (int) Math.ceil(length / spacing)));
        List<Vec3> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            points.add(start.lerp(end, (double) i / count));
        }
        return points;
    }
}
