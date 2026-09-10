package com.dimalab.storymodengine.client.network.example;

import com.dimalab.storymodengine.common.network.example.PingServer;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /sme nettest ping} — the client → server half; see {@link NetworkTestCommand}. */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class NetworkTestClientCommand {

    private NetworkTestClientCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("nettest")
                        .then(Commands.literal("ping").executes(context -> {
                            Network.sendToServer(new PingServer(System.currentTimeMillis()));
                            return 1;
                        }))));
    }
}
