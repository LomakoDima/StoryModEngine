package com.dimalab.storymodengine.client.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Displays a pre-formatted quest notification in the action bar — {@code QuestToastPacket}'s own
 * handler, split into its own small class rather than inlined there so it stays trivially swappable
 * (a mod author who wants a fancier on-screen toast can replace {@link #show} without touching the
 * packet). Purely display — decides nothing (see the design doc §11).
 */
public final class QuestToast {

    private QuestToast() {
    }

    public static void show(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("[Quest] " + message), true);
        }
    }
}
