package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one new client input this system needs: a dedicated "advance the dialogue" key, distinct
 * from a raw mouse click. {@link DialogueWindow} is a non-modal overlay drawn over live gameplay —
 * a click on it would still reach the world underneath (swing the held item, place a block), so
 * "Continue" can't reuse a click the way the old single-{@code Screen} version could. Defaults to
 * {@code G}, a key vanilla leaves unbound; the player can rebind it in Controls like any other.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DialogueContinueKeyMapping {

    public static final String CATEGORY = "key.categories.storymodengine";

    public static final KeyMapping CONTINUE = new KeyMapping(
            "key.storymodengine.dialogue_continue", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);

    private DialogueContinueKeyMapping() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONTINUE);
    }
}
