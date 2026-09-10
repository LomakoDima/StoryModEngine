package com.dimalab.storymodengine.common.shop;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /sme shop open <name>} — {@link ShopStoryCommands}'s fast-iteration debug counterpart, same
 * role every other script command's debug sibling already has. Opens for the executing player
 * directly — no "nearest NPC" needed, since a shop isn't tied to any particular entity.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class ShopCommand {

    private ShopCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("shop")
                        .then(Commands.literal("open")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "name")))))));
    }

    private static int open(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ShopSystem.open(player, name);
        return 1;
    }
}
