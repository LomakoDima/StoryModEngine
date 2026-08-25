package com.dimalab.storymodengine.common.cinematic;

import com.dimalab.storymodengine.common.cinematic.discovery.CutsceneDiscovery;
import com.dimalab.storymodengine.common.cinematic.integration.CinematicTickBridge;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Called once from {@code EngineBootstrap.init}, after {@code FlowBootstrap} — mirrors it exactly:
 * {@link CutsceneDiscovery} finds every {@code @AutoCutscene} field (the network/capability
 * discovery passes {@code EngineBootstrap} already ran find {@code @Packet}/{@code @Capability}
 * automatically, nothing cinematic-specific needed there), then {@link CinematicTickBridge} arms
 * the one new server tick source {@link CinematicManager} needs.
 */
public final class CinematicBootstrap {

    private CinematicBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        CutsceneDiscovery.run(modId);
        CinematicTickBridge.registerOnce();
    }
}
