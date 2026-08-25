package com.dimalab.storymodengine.client.content;

import com.dimalab.storymodengine.common.content.ContentDescriptor;
import com.dimalab.storymodengine.common.content.ContentDiscovery;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives every {@code @AutoContent}-discovered {@link SimpleParticleType} a working renderer
 * ({@link AutoParticle}) without the mod author writing any client code for it — the same "just
 * works by convention" deal {@code AutoFluidType} gives fluids.
 *
 * <p>{@code RegisterParticleProvidersEvent} only ever fires on the logical client (verified against
 * its own Javadoc), but merely referencing its class in code that also runs on a dedicated server
 * is safe — Forge deliberately leaves this event class itself free of {@code @OnlyIn} for exactly
 * that reason. What is <em>not</em> safe on a dedicated server is loading {@link AutoParticle} (a
 * true {@code @OnlyIn(Dist.CLIENT)} type, extending {@code TextureSheetParticle}), which is why
 * {@link #register} is only ever called from behind the {@code Dist.CLIENT} check in {@code
 * EngineBootstrap} — evaluating {@code AutoParticle.Provider::new} anywhere in this class's own
 * bytecode is exactly what would force that class to resolve, so this class must never be reached
 * on a server.
 */
public final class AutoParticleRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoParticleRegistration.class);

    private AutoParticleRegistration() {
    }

    public static void register(IEventBus modEventBus, String modId) {
        modEventBus.addListener((RegisterParticleProvidersEvent event) -> onRegister(event, modId));
    }

    private static void onRegister(RegisterParticleProvidersEvent event, String modId) {
        int registered = 0;
        for (ContentDescriptor<?> descriptor : ContentDiscovery.getDiscovered()) {
            if (descriptor.id().getNamespace().equals(modId)
                    && SimpleParticleType.class.isAssignableFrom(descriptor.contentType())) {
                @SuppressWarnings("unchecked")
                ContentDescriptor<SimpleParticleType> particle = (ContentDescriptor<SimpleParticleType>) descriptor;
                event.registerSprite(particle.supplier().get(), new AutoParticle.Provider());
                registered++;
            }
        }
        LOGGER.info("[{}] Registered the default renderer for {} @AutoContent particle(s)", modId, registered);
    }
}
