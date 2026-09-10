package com.dimalab.storymodengine.common.cinematic.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.json.CutsceneJsonLoader;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * {@code /sme cutscene playjson <name>} — loads and immediately plays a JSON cutscene
 * straight off disk from {@code <server directory>/storymodengine/cutscenes/<name>.json}, the exact
 * file {@code cinematic.client.CameraRecorderCommands} writes. Exists specifically to remove the
 * "copy the file into a data pack, then {@code /reload}" round-trip a mod author would otherwise
 * need just to preview a freshly recorded path — this reuses {@link CutsceneJsonLoader#parse}
 * directly (same parser, same validation as a real data pack file) rather than going through
 * Minecraft's {@code ResourceManager}/reload pipeline at all. A cutscene meant to ship permanently
 * still belongs in an actual data pack (so it loads automatically on every boot, not only when
 * someone remembers to run this command) — this is a preview/iteration tool, not a replacement for
 * that.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CutscenePlayJsonCommand {

    private CutscenePlayJsonCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("cutscene")
                        .then(Commands.literal("playjson")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(CutscenePlayJsonCommand::run)))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");
        File file = context.getSource().getServer().getFile("storymodengine/cutscenes/" + name + ".json");

        if (!file.isFile()) {
            EngineLog.channel("Cinematic").error(
                    "No such file: {} — record one with /sme cinematic camrecord start|stop first", file.getPath()).toChat(player);
            return 0;
        }

        ResourceLocation id = new ResourceLocation(StoryModEngine.MODID, "recorded_" + name);
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            CutsceneDefinition definition = CutsceneJsonLoader.parse(id, json.getAsJsonObject());
            CutsceneRegistry.register(definition);
            CinematicManager.play(player, definition, Map.of());
            EngineLog.channel("Cinematic").success("Playing '{}' from {}", id, file.getPath()).toChat(player);
        } catch (IOException | RuntimeException e) {
            EngineLog.channel("Cinematic").error("Failed to load '" + name + "' — " + e.getMessage(), e).toChat(player);
            return 0;
        }
        return 1;
    }
}
