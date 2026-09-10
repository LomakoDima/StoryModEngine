package com.dimalab.storymodengine.common.scripting.reload;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers {@link SmeReloadListener} with Minecraft's resource-reload pipeline — mirrors {@code dialogue.json.DialogueJsonBootstrap} exactly, including being pinned to {@link StoryModEngine#MODID} rather than a generic mod id. */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class SmeReloadBootstrap {

    private SmeReloadBootstrap() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SmeReloadListener());
    }
}
