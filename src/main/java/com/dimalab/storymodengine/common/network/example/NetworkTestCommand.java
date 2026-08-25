package com.dimalab.storymodengine.common.network.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.network.Network;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /storymodengine nettest broadcast} and {@code /storymodengine nettest sync} — server-side
 * halves of the networking smoke test; {@code /storymodengine nettest ping} (client-side, since it
 * needs to run without a player having typed anything server-authoritative) is {@link
 * NetworkTestClientCommand}.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class NetworkTestCommand {

    private NetworkTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("nettest")
                        .then(Commands.literal("broadcast").executes(NetworkTestCommand::broadcast))
                        .then(Commands.literal("sync").executes(NetworkTestCommand::sync))));
    }

    private static int broadcast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<UUID> online = source.getServer().getPlayerList().getPlayers().stream()
                .map(Entity::getUUID).toList();

        Network.sendToAll(new BroadcastAnnouncement(
                "Hello from the server!",
                online,
                Optional.of(0x55FFFF),
                new Vector3f((float) source.getPosition().x, (float) source.getPosition().y, (float) source.getPosition().z)));

        source.sendSuccess(() -> Component.literal("[StoryModEngine] Sent BroadcastAnnouncement to all players."), false);
        return 1;
    }

    private static int sync(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Network.sendToPlayer(player, new SyncPlayerState(player.getUUID(), player.position(), player.getHealth()));
        context.getSource().sendSuccess(() -> Component.literal("[StoryModEngine] Sent SyncPlayerState to you."), false);
        return 1;
    }
}
