package com.dimalab.storymodengine.common.cinematic.persistence;

import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.event.bridge.PlayerConnectedEvent;

/**
 * {@code Forge PlayerLoggedInEvent → MinecraftEventBridge → PlayerConnectedEvent → EventBus →
 * Cinematic} — the exact same chain {@code flow.integration.event.FlowJoinBridge} already
 * demonstrates for Flow, reused here rather than invented fresh.
 */
public final class CinematicJoinBridge {

    private CinematicJoinBridge() {
    }

    @SubscribeEvent
    public static void onPlayerConnected(PlayerConnectedEvent event) {
        CinematicManager.resumeAll(event.player());
    }
}
