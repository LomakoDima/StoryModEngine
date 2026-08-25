package com.dimalab.storymodengine.client.event.example;

import com.dimalab.storymodengine.common.event.example.EventTestPacket;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * {@code /storymodengine eventtest network} — the client → server half of the network/math/
 * capability demo, mirroring {@code NetworkTestClientCommand}. Sends the player's current position
 * as the packet payload; see {@link EventTestPacket}/{@link EventTestListeners#onNetworkTriggered}
 * for what happens once it arrives.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class EventTestClientCommand {

    private EventTestClientCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("eventtest")
                        .then(Commands.literal("network").executes(context -> {
                            var pos = Minecraft.getInstance().player.position();
                            Network.sendToServer(new EventTestPacket(
                                    new Vector3f((float) pos.x, (float) pos.y, (float) pos.z)));
                            return 1;
                        }))));
    }
}
