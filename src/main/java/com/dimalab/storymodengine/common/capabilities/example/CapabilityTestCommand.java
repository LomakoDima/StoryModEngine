package com.dimalab.storymodengine.common.capabilities.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /storymodengine capability} shows the executing player's current {@link StoryPlayerData};
 * {@code /storymodengine capability add <amount>} mutates it, marks it dirty, synchronizes it to
 * the player via the existing {@code network} system, and persists automatically the next time the
 * entity/world saves — nothing here writes NBT or a packet by hand.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CapabilityTestCommand {

    private CapabilityTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("capability")
                        .executes(CapabilityTestCommand::show)
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(CapabilityTestCommand::add)))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        StoryPlayerData data = Capabilities.get(player, ModCapabilities.STORY_DATA);
        EngineLog.success("Story Points: {} | Met the guide: {} | Quests completed: {}",
                data.storyPoints, data.metTheGuide, data.questsCompleted).toChat(player);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");

        StoryPlayerData data = Capabilities.get(player, ModCapabilities.STORY_DATA);
        data.storyPoints += amount;

        Capabilities.markDirty(player, ModCapabilities.STORY_DATA);
        ModCapabilities.STORY_DATA.sync(player);

        EngineLog.success("Story Points: {}", data.storyPoints).toChat(player);
        return 1;
    }
}
