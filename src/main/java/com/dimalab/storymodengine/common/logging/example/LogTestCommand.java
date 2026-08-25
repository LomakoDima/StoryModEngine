package com.dimalab.storymodengine.common.logging.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.ChatTemplate;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.logging.EngineLogger;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /storymodengine logtest} — exercises every capability {@code logging} was asked for in
 * one place: all six levels, a placeholder, a raw {@link Component}, a style/color override, a
 * broadcast-to-everyone chat send, a send-to-one-player chat send, console-only output, and a
 * second, independently-named channel. Works identically whether run by a player (singleplayer or
 * multiplayer) or from a dedicated server's console (no player at all) — see the comments below
 * for which lines specifically demonstrate that.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class LogTestCommand {

    private LogTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("logtest")
                        .executes(LogTestCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // Console only — the plain, boilerplate-free call. Works on any thread, any side, with
        // or without a player, singleplayer or dedicated server: it's just an SLF4J log call.
        EngineLog.trace("Trace: verbose internals");
        EngineLog.debug("Debug: development detail, count={}", 42);
        EngineLog.info("Info: {} command executed", "logtest");
        EngineLog.success("Success: demo step completed");
        EngineLog.warn("Warning: this is only a demo, nothing is actually wrong");
        EngineLog.error("Error: simulated failure for demonstration purposes");

        // Console + chat, broadcast to every player currently online. On a dedicated server with
        // zero players connected this is a safe no-op chat-wise (console line still happens).
        EngineLog.success("Broadcast: hello to everyone currently online!").toChat();

        // Console + chat, with a custom color overriding the level's default.
        EngineLog.info("Broadcast: this one is styled").color(ChatFormatting.LIGHT_PURPLE).toChat();

        // Console + chat to one specific player — only meaningful if a player ran the command.
        try {
            ServerPlayer player = source.getPlayerOrException();
            EngineLog.warn("Private: only you can see this warning").toChat(player);
        } catch (CommandSyntaxException e) {
            EngineLog.info("No player ran this command (e.g. dedicated server console) — skipped the single-player chat line");
        }

        // A second, independent channel — how another subsystem (or another mod entirely) would
        // use the same system under its own name and its own level filter.
        EngineLogger network = EngineLogger.of("StoryModEngine/Network");
        network.info("Custom channel example: {} bytes sent", 128);

        // A raw Minecraft Component instead of a plain string — its own explicit style (red, bold)
        // is preserved; only the surrounding [StoryModEngine]: prefix comes from the template.
        EngineLog.info(Component.literal("Component example: ")
                        .append(Component.literal("bold red text").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)))
                .toChat();

        // A fully custom per-channel ChatTemplate: RGB colors (not just the 16 legacy
        // ChatFormatting ones), bold/italic, custom bracket characters and separator text, and a
        // message color that overrides the level's default (SUCCESS is normally green; here it's
        // forced to white). EngineLog.channel("Story") is EngineLogger.of("Story") under a
        // shorter name — the [Story]: prefix and this template are entirely independent of
        // "StoryModEngine"'s own default template.
        EngineLogger story = EngineLog.channel("Story");
        story.setChatTemplate(ChatTemplate.DEFAULT
                .withBrackets("<", ">")
                .withSeparator(" » ")
                .withBracketStyle(Style.EMPTY.withColor(0x55FFFF).withBold(true))
                .withNameStyle(Style.EMPTY.withColor(0xFFAA00).withItalic(true))
                .withSeparatorStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY))
                .withMessageStyle(Style.EMPTY.withColor(0xFFFFFF)));
        story.success("Chapter loaded — fully custom RGB template").toChat();

        source.sendSuccess(() -> Component.literal("[StoryModEngine] Logging demo executed — check console and chat."), false);
        return 1;
    }
}
