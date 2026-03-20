package com.dimalab.storymodengine.events;

import com.dimalab.storymodengine.chat.SmeChat;
import com.dimalab.storymodengine.StoryModEngine;
import com.dimalab.storymodengine.logging.LogSubsystem;
import com.dimalab.storymodengine.logging.SmeLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEvents {
    private ForgeEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Player-facing chat message system (not logging).
        SmeChat.broadcast("Добро пожаловать в мир, {}!", player.getGameProfile().getName());

        // Logging system example.
        SmeLog.info(LogSubsystem.EVENTS, "Player logged in: name={}, uuid={}, level={}",
                player.getGameProfile().getName(),
                player.getUUID(),
                player.level().dimension().location());

        // Example of anti-spam logging (only once per session per key).
        SmeLog.onceInfo(LogSubsystem.EVENTS, "welcome_hint", "Welcome message was shown at least once this session.");
    }
}

