package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.common.flow.discovery.FlowDiscovery;
import com.dimalab.storymodengine.common.flow.integration.FlowTickBridge;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Called once from {@code EngineBootstrap.init}, last — Flow persistence ({@code @Capability}) and
 * the join-resume listener ({@code @SubscribeEvent}) are both found automatically by discovery
 * mechanisms {@code EngineBootstrap.init} already ran earlier in the same call. This runs {@link
 * FlowDiscovery} (finds every {@code @AutoFlow} field) and arms the one genuinely new Forge
 * touch-point: the server tick source.
 */
public final class FlowBootstrap {

    private FlowBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        FlowDiscovery.run(modId);
        FlowTickBridge.registerOnce();
    }
}
