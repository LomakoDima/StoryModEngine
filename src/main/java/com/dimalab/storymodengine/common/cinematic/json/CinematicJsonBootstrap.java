package com.dimalab.storymodengine.common.cinematic.json;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers {@link CutsceneJsonLoader} with Minecraft's own resource-reload pipeline — the standard
 * hook every vanilla data-driven system (recipes, loot tables) registers through, verified directly
 * against the Forge 1.20.1 sources before relying on it. Fires on world (re)load and {@code
 * /reload}, so a JSON cutscene edited in a data pack shows up without a restart.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CinematicJsonBootstrap {

    private CinematicJsonBootstrap() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CutsceneJsonLoader());
    }
}
