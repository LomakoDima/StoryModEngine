package com.dimalab.storymodengine.common.trigger;

import com.dimalab.storymodengine.common.trigger.discovery.TriggerDiscovery;
import com.dimalab.storymodengine.common.trigger.integration.TriggerTickBridge;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * {@code trigger} needs a tick source of its own (for {@code LOCATION}/{@code TIME} polling — see
 * {@code integration.TriggerTickBridge}), so this mirrors {@code CinematicBootstrap}/{@code
 * FlowBootstrap}'s two-line shape rather than {@code QuestBootstrap}'s tick-less one-liner.
 */
public final class TriggerBootstrap {

    private TriggerBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        TriggerDiscovery.run(modId);
        TriggerTickBridge.registerOnce();
    }
}
