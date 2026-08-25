package com.dimalab.storymodengine.common.event;

import com.dimalab.storymodengine.common.event.bridge.MinecraftEventBridge;
import com.dimalab.storymodengine.common.event.discovery.EventListenerDiscovery;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Called once from {@code EngineBootstrap.init}, after content/network/capability discovery:
 * discovers every {@code @SubscribeEvent} static listener in the calling mod's own jar, posts
 * {@link AnnotationProcessorEvent} marking this mod's engine setup complete, then arms the
 * Minecraft↔engine event bridge. No {@code Dist} gate — discovery and posting behave identically on
 * a dedicated server (neither touches a client-only type).
 */
public final class EventBootstrap {

    private EventBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        EventListenerDiscovery.run(modId, Events.bus());
        Events.post(new AnnotationProcessorEvent(modId));
        MinecraftEventBridge.registerOnce();
    }
}
