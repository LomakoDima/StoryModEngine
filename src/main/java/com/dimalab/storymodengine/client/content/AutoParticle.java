package com.dimalab.storymodengine.client.content;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The default renderer a {@code SimpleParticleType} discovered via {@code @AutoContent} gets for
 * free — a plain textured, gravity-affected, fading billboard reading the single sprite {@code
 * AssetGenerator} already describes for it at {@code particles/<id>.json} (texture at the
 * conventional {@code textures/particle/<id>.png}). No sprite-assignment code is needed here:
 * registering {@link Provider} through {@code RegisterParticleProvidersEvent#registerSprite} makes
 * {@code ParticleEngine} call {@link #pickSprite} on the created particle automatically (verified
 * against {@code ParticleEngine#register(ParticleType, ParticleProvider.Sprite)} source) — the same
 * reason the inherited {@code Particle#tick()} (gravity, friction, motion, lifetime/removal) is
 * left untouched below; only the fade is this class's own addition.
 *
 * <p>A mod author who wants bespoke particle behavior instead of this convention writes their own
 * {@code Particle} subclass and registers it directly against {@code
 * RegisterParticleProvidersEvent} — {@code AutoParticle} only exists so the common case ("a
 * particle that looks like its texture and falls/fades like a puff of something") needs zero
 * client code.
 */
@OnlyIn(Dist.CLIENT)
public class AutoParticle extends TextureSheetParticle {

    private AutoParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.gravity = 1.0F;
        this.lifetime = 20 + this.random.nextInt(20);
    }

    @Override
    public void tick() {
        super.tick();
        this.setAlpha(1.0F - (float) this.age / (float) this.lifetime);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider.Sprite<SimpleParticleType> {
        @Override
        public TextureSheetParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                     double x, double y, double z,
                                                     double dx, double dy, double dz) {
            return new AutoParticle(level, x, y, z, dx, dy, dz);
        }
    }
}
