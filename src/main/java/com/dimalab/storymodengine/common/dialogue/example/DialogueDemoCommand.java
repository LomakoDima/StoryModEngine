package com.dimalab.storymodengine.common.dialogue.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.DialogueHandle;
import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /sme dialogue demo} — starts {@link DialogueExamples#ENCOUNTER}, the required
 * demonstration: several consecutive lines (one an {@code INTERNAL_THOUGHT}, rendered by the
 * non-modal {@code DialogueWindow} overlay with typewriter reveal), then a three-option branch that
 * opens the modal {@code DialogueScreen} and leads to a distinct closing line per choice. {@code
 * dialogue start <id>} starts any registered dialogue by id (a bare path under {@code
 * storymodengine:}, or the JSON demo's own namespaced id — e.g. {@code village_guard} or {@code
 * merchant}); {@code dialogue stop} cancels whatever's active.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class DialogueDemoCommand {

    private DialogueDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("dialogue")
                        .then(Commands.literal("demo").executes(DialogueDemoCommand::demo))
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(DialogueDemoCommand::start)))
                        .then(Commands.literal("stop").executes(DialogueDemoCommand::stop))));
    }

    private static int demo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DialogueHandle handle = DialogueSystem.start(player, DialogueExamples.ENCOUNTER);
        if (handle == null) {
            EngineLog.channel("Dialogue").error("Failed to start the demo dialogue — see console").toChat(player);
            return 0;
        }
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String path = StringArgumentType.getString(context, "id");
        ResourceLocation id = path.contains(":") ? new ResourceLocation(path) : new ResourceLocation(StoryModEngine.MODID, path);
        DialogueDefinition definition = DialogueRegistry.get(id);
        if (definition == null) {
            EngineLog.channel("Dialogue").error("No dialogue registered under '{}'", id).toChat(player);
            return 0;
        }
        DialogueSystem.start(player, definition);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DialogueSystem.stop(player);
        EngineLog.channel("Dialogue").info("Dialogue stopped.").toChat(player);
        return 1;
    }
}
