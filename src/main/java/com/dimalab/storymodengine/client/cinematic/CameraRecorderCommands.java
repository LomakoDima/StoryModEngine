package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /storymodengine cinematic camrecord start|stop <name>} — records the local player's
 * position/rotation once per client tick into an in-memory list, then writes it out as a ready-to-
 * edit JSON cutscene (the exact schema {@code cinematic.json.CutsceneJsonLoader} reads) once
 * stopped. Deliberately commands only, no GUI screen — the task this was built from explicitly
 * asked to keep it that way, since a full in-game editor was already built once in this project and
 * explicitly removed at the user's request; this is a narrower, single-purpose capture tool, not a
 * reintroduction of that editor.
 *
 * <p>Only camera keyframes are captured — no actors, subtitles, or audio. The written file lives
 * under the game directory (not inside any data pack), so it does *not* auto-load through the
 * normal {@code CutsceneJsonLoader}/reload path by itself — {@code /storymodengine cutscene
 * playjson <name>} (see {@code cinematic.example.CutscenePlayJsonCommand}) plays it directly off
 * disk for quick iteration, no copying or {@code /reload} needed (this project's own {@code
 * runClient}/{@code runServer} Gradle tasks share one {@code run/} directory, so the file the
 * client writes and the file the server reads are the same file). A cutscene meant to ship
 * permanently should still be promoted into an actual data pack's {@code
 * data/<namespace>/storymodengine/cutscenes/} folder eventually, so it loads automatically on every
 * boot rather than only when someone remembers to run the preview command.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class CameraRecorderCommands {

    private static boolean recording;
    private static int tick;
    private static final List<Frame> FRAMES = new ArrayList<>();

    private CameraRecorderCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("cinematic")
                        .then(Commands.literal("camrecord")
                                .then(Commands.literal("start").executes(CameraRecorderCommands::start))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(CameraRecorderCommands::stop))))));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !recording) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        FRAMES.add(new Frame(tick, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        tick++;
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        if (recording) {
            feedback(ChatFormatting.RED, "Already recording — stop it first.");
            return 0;
        }
        FRAMES.clear();
        tick = 0;
        recording = true;
        feedback("Recording camera path... run '/storymodengine cinematic camrecord stop <name>' when done.");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!recording) {
            feedback(ChatFormatting.RED, "Not currently recording.");
            return 0;
        }
        recording = false;
        String name = StringArgumentType.getString(context, "name");
        if (FRAMES.isEmpty()) {
            feedback(ChatFormatting.RED, "Nothing was recorded — not writing an empty cutscene.");
            return 0;
        }
        try {
            Path path = writeJson(name);
            feedback("Saved " + FRAMES.size() + " tick(s) to " + path
                    + " — try it now with /storymodengine cutscene playjson " + name);
        } catch (IOException e) {
            EngineLog.channel("Cinematic").error("Failed to write recorded camera path '" + name + "'", e);
            feedback(ChatFormatting.RED, "Failed to save — see the log.");
            return 0;
        }
        return 1;
    }

    private static Path writeJson(String name) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("duration", FRAMES.size());

        JsonArray shots = new JsonArray();
        JsonObject shot = new JsonObject();
        shot.addProperty("start", 0);
        shot.addProperty("duration", FRAMES.size());

        JsonArray position = new JsonArray();
        JsonArray rotation = new JsonArray();
        for (Frame frame : FRAMES) {
            JsonObject pos = new JsonObject();
            pos.addProperty("tick", frame.tick());
            pos.addProperty("x", frame.x());
            pos.addProperty("y", frame.y());
            pos.addProperty("z", frame.z());
            position.add(pos);

            JsonObject rot = new JsonObject();
            rot.addProperty("tick", frame.tick());
            rot.addProperty("yaw", frame.yaw());
            rot.addProperty("pitch", frame.pitch());
            rotation.add(rot);
        }
        shot.add("position", position);
        shot.add("rotation", rotation);
        shots.add(shot);
        root.add("shots", shots);

        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("storymodengine").resolve("cutscenes");
        Files.createDirectories(dir);
        Path path = dir.resolve(name + ".json");
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        return path;
    }

    private static void feedback(String message) {
        feedback(ChatFormatting.GRAY, "[Cinematic] " + message);
    }

    private static void feedback(ChatFormatting color, String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message).withStyle(color), false);
        }
    }

    private record Frame(int tick, double x, double y, double z, float yaw, float pitch) {
    }
}
