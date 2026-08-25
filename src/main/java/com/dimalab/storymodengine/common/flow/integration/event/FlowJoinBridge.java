package com.dimalab.storymodengine.common.flow.integration.event;

import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.event.bridge.PlayerConnectedEvent;
import com.dimalab.storymodengine.common.flow.FlowManager;

/**
 * {@code Forge PlayerLoggedInEvent → MinecraftEventBridge → PlayerConnectedEvent → EventBus → Flow}
 * — the exact chain the task asked to demonstrate, built entirely from infrastructure that already
 * existed before this package: a static listener, discovered and registered automatically by the
 * already-existing {@code EventListenerDiscovery} with no code anywhere calling {@code
 * Events.register} for it.
 */
public final class FlowJoinBridge {

    private FlowJoinBridge() {
    }

    @SubscribeEvent
    public static void onPlayerConnected(PlayerConnectedEvent event) {
        FlowManager.resumeAll(event.player());
    }
}
