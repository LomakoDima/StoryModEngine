package com.dimalab.storymodengine.client.raycast;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The one class in {@code raycast} allowed to reference {@code ClientLevel} — see {@link
 * RaycastDebug}'s doc for why this has to live in its own class rather than a branch inside it.
 * {@link #spawn}'s own signature takes {@code Level}, not {@code ClientLevel} (matching {@code
 * network.context.ClientLevelLookup#get()}'s identical choice), so nothing about *calling* this
 * class forces {@code ClientLevel} to resolve — only this method's body does, and it only runs
 * behind {@link RaycastDebug}'s own {@code instanceof ServerLevel} check having already failed.
 * {@code public} only because the api/client/common split now puts it in a different package than
 * {@code RaycastDebug} — visibility has no bearing on the dist-safety property above, which comes
 * from lazy class-loading, not access modifiers.
 */
public final class RaycastClientDebug {

    private RaycastClientDebug() {
    }

    public static void spawn(Level level, List<Vec3> points, ParticleOptions particle, Vec3 hitPoint) {
        ClientLevel clientLevel = (ClientLevel) level;
        for (Vec3 point : points) {
            clientLevel.addParticle(particle, point.x, point.y, point.z, 0.0, 0.0, 0.0);
        }
        if (hitPoint != null) {
            for (int i = 0; i < 8; i++) {
                clientLevel.addParticle(particle, hitPoint.x, hitPoint.y, hitPoint.z, 0.0, 0.0, 0.0);
            }
        }
    }
}
