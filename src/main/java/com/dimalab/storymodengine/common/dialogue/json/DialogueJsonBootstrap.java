package com.dimalab.storymodengine.common.dialogue.json;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers {@link DialogueJsonLoader} with Minecraft's resource-reload pipeline — mirrors {@code
 * cinematic.json.CinematicJsonBootstrap} exactly, including being pinned to {@link
 * StoryModEngine#MODID} rather than a generic mod id (the same existing scope that class has).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class DialogueJsonBootstrap {

    private DialogueJsonBootstrap() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DialogueJsonLoader());
    }
}
