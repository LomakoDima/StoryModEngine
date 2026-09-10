package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /sme titlecard demo} — the required live demonstration: a full-screen black
 * card fading in over "Тем временем...", holding on "Где-то глубоко под землёй", then fading back
 * out, exactly the example the title-card task was built from. {@code /sme titlecard
 * show "<title>" ["<subtitle>"]} is the general-purpose version for custom testing — quoted
 * strings, since Brigadier's plain word argument can't hold spaces.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class TitleCardDemoCommand {

    private TitleCardDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("titlecard")
                        .then(Commands.literal("demo").executes(TitleCardDemoCommand::demo))
                        .then(Commands.literal("show")
                                .then(Commands.argument("title", StringArgumentType.string())
                                        .executes(ctx -> show(ctx, null))
                                        .then(Commands.argument("subtitle", StringArgumentType.string())
                                                .executes(ctx -> show(ctx, StringArgumentType.getString(ctx, "subtitle"))))))));
    }

    private static int demo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TitleCard titleCard = TitleCard.builder(Component.literal("Тем временем..."))
                .subtitle(Component.literal("Где-то глубоко под землёй"))
                .fadeIn(20)
                .hold(60)
                .fadeOut(20)
                .build();
        CinematicManager.playTitleCard(player, titleCard);
        EngineLog.channel("Cinematic").success("Title card demo started.").toChat(player);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context, String subtitle) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String title = StringArgumentType.getString(context, "title");
        TitleCard.Builder builder = TitleCard.builder(Component.literal(title));
        if (subtitle != null) {
            builder.subtitle(Component.literal(subtitle));
        }
        CinematicManager.playTitleCard(player, builder.build());
        EngineLog.channel("Cinematic").success("Title card started.").toChat(player);
        return 1;
    }
}
