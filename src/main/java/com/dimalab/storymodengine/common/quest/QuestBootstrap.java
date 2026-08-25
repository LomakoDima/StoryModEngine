package com.dimalab.storymodengine.common.quest;

import com.dimalab.storymodengine.common.quest.discovery.QuestDiscovery;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Calls only {@code QuestDiscovery.run(modId)} — Quest needs no tick source of its own (it rides
 * {@code FlowManager}, already armed by {@code FlowBootstrap}) and no separate persistence wiring
 * ({@code @Capability} discovery already found {@code ModQuestCapabilities.PROGRESS_DATA}). Called
 * from {@code core.EngineBootstrap.init}, last — after {@code DialogueBootstrap.init()} — since
 * Quest is the one subsystem that depends on Flow, Dialogue, Capability, Network, and Event all
 * being fully initialized already (see the design doc §15).
 */
public final class QuestBootstrap {

    private QuestBootstrap() {
    }

    public static void init() {
        String modId = ModLoadingContext.get().getContainer().getModId();
        QuestDiscovery.run(modId);
    }
}
