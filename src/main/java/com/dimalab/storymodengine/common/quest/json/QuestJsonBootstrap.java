package com.dimalab.storymodengine.common.quest.json;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers {@link QuestJsonLoader} — byte-for-byte {@code dialogue.json.DialogueJsonBootstrap}'s own shape. */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class QuestJsonBootstrap {

    private QuestJsonBootstrap() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new QuestJsonLoader());
    }
}
