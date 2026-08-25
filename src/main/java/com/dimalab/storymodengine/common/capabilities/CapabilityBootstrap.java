package com.dimalab.storymodengine.common.capabilities;

import com.dimalab.storymodengine.common.capabilities.discovery.CapabilityDiscovery;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Wires one mod's {@code @Capability} declarations into the engine — discovery adds them to the
 * global {@link com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry}, then {@link
 * CapabilityLifecycle#registerOnce} arms the (JVM-wide, idempotent — see its Javadoc) attachment
 * listeners. Called from {@code EngineBootstrap.init}, same shape as {@code ContentDiscovery.run}/
 * {@code NetworkBootstrap.init}. No {@code Dist} gate: discovery and attachment are common-side
 * (identical on a dedicated server — verified the same way {@code NetworkBootstrap} was).
 */
public final class CapabilityBootstrap {

    private CapabilityBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        String modId = ModLoadingContext.get().getContainer().getModId();
        CapabilityDiscovery.run(modId);
        CapabilityLifecycle.registerOnce(modEventBus);
    }
}
