package com.dimalab.storymodengine.client.entity.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /sme npc pathdebug} — registers under the same shared {@code storymodengine} root
 * {@code ModelCommand}/{@code NpcCommand}/etc. already do (Brigadier merges every {@code register()}
 * call under one literal, already relied on elsewhere in this codebase). A client command, since the
 * renderer it toggles ({@link NpcPathDebugRenderer}) only exists on {@code Dist.CLIENT}.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class NpcPathDebugCommand {

    private NpcPathDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("npc")
                        .then(Commands.literal("pathdebug").executes(context -> pathdebug()))));
    }

    private static int pathdebug() {
        boolean enabled = NpcPathDebugRenderer.toggle();
        message(enabled ? "NPC path debug: ON" : "NPC path debug: OFF");
        return enabled ? 1 : 0;
    }

    private static void message(String text) {
        EngineLog.channel("Npc").info(text);
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
}
