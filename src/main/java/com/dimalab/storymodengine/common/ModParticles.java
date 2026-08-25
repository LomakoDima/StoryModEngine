package com.dimalab.storymodengine.common;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.core.particles.SimpleParticleType;

import java.util.function.Supplier;

/**
 * A mod author's own particle class — same shape as {@link ModItems}/{@link ModEffects}:
 * {@link AutoContent} infers the id ({@code sparkle}) from the field name and routes it to
 * {@code ForgeRegistries.PARTICLE_TYPES} from the field's {@code SimpleParticleType} type.
 *
 * <p>{@code new SimpleParticleType(false)} — the {@code false} is Minecraft's own
 * {@code overrideLimiter} flag (whether this particle ignores the client's particle-count setting,
 * like vanilla's damage/critical hit particles do); {@code false} is the normal choice for a
 * decorative effect. No renderer needs writing: {@code AutoParticleRegistration} already gives
 * every discovered {@code SimpleParticleType} a default textured, gravity-affected, fading
 * renderer. Put the texture at {@code assets/storymodengine/textures/particle/sparkle.png} — any
 * roughly-square size works, no exact-match requirement like paintings have.
 *
 * <p>Try it in-game with {@code /particle storymodengine:sparkle ~ ~1 ~}.
 */
public class ModParticles {

    @AutoContent
    public static final Supplier<SimpleParticleType> SPARKLE = ContentHolder.of(() -> new SimpleParticleType(false));
}
