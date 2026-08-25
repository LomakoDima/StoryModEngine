package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.common.dialogue.discovery.DialogueDiscovery;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Called once from {@code EngineBootstrap.init}, after {@code CinematicBootstrap} — mirrors {@code
 * FlowBootstrap}/{@code CinematicBootstrap} exactly. Runs {@link DialogueDiscovery} (finds every
 * {@code @AutoDialogue} field). Needs no tick-source bootstrap of its own: everything that actually
 * runs over time is the compiled {@code Flow}, already driven by {@code flow.integration.FlowTickBridge}.
 */
public final class DialogueBootstrap {

    private DialogueBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        DialogueDiscovery.run(modId);
    }
}
