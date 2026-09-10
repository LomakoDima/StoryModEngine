package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.common.cinematic.network.CutsceneControlAction;
import com.dimalab.storymodengine.common.cinematic.network.RequestCutsceneControlPacket;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.network.Network;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runtime playback controls for whichever cutscene is currently active — {@code /sme
 * cutscene pause|resume|seek|jumpto|speed|skip}. Registered client-side ({@link
 * RegisterClientCommandsEvent}, since only the client knows "is a cutscene currently playing" at
 * all), but every action is now a *request* sent to the server (@code RequestCutsceneControlPacket})
 * rather than a direct local mutation — {@code CinematicManager} is the one that actually decides
 * (and can refuse, e.g. a forward seek on a {@code NON_SKIPPABLE} cutscene), replying with the
 * authoritative state via {@code SyncCutscenePlaybackPacket}, which {@code ClientCutscenePlayer}
 * applies. See {@code ARCHITECTURE.md} for why this replaced the earlier, purely client-local
 * version of these commands.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class CutsceneControlCommands {

    private CutsceneControlCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("cutscene")
                        .then(Commands.literal("pause").executes(CutsceneControlCommands::pause))
                        .then(Commands.literal("resume").executes(CutsceneControlCommands::resume))
                        .then(Commands.literal("skip").executes(CutsceneControlCommands::skip))
                        .then(Commands.literal("seek")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(CutsceneControlCommands::seek)))
                        .then(Commands.literal("jumpto")
                                .then(Commands.argument("marker", StringArgumentType.word())
                                        .executes(CutsceneControlCommands::jumpTo)))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.01f, 8f))
                                        .executes(CutsceneControlCommands::speed)))));
    }

    private static int pause(CommandContext<CommandSourceStack> context) {
        return withActiveCutscene(id -> {
            request(id, CutsceneControlAction.PAUSE, 0, 1f);
            feedback("Pause requested.");
        });
    }

    private static int resume(CommandContext<CommandSourceStack> context) {
        return withActiveCutscene(id -> {
            request(id, CutsceneControlAction.RESUME, 0, 1f);
            feedback("Resume requested.");
        });
    }

    private static int skip(CommandContext<CommandSourceStack> context) {
        return withActiveCutscene(id -> {
            request(id, CutsceneControlAction.SKIP, 0, 1f);
            feedback("Skip requested.");
        });
    }

    private static int seek(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        return withActiveCutscene(id -> {
            request(id, CutsceneControlAction.SEEK, ticks, 1f);
            feedback("Seek to tick " + ticks + " requested.");
        });
    }

    private static int jumpTo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String marker = StringArgumentType.getString(context, "marker");
        return withActiveCutscene(id -> {
            CutsceneDefinition definition = CutsceneRegistry.get(id);
            Integer tick = definition == null ? null : definition.timeline().markerTick(marker);
            if (tick == null) {
                feedback(ChatFormatting.RED, "No such marker: '" + marker + "'.");
                return;
            }
            request(id, CutsceneControlAction.SEEK, tick, 1f);
            feedback("Jump to marker '" + marker + "' requested.");
        });
    }

    private static int speed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float multiplier = FloatArgumentType.getFloat(context, "multiplier");
        return withActiveCutscene(id -> {
            request(id, CutsceneControlAction.SPEED, 0, multiplier);
            feedback("Speed " + multiplier + "x requested.");
        });
    }

    private static void request(ResourceLocation cutsceneId, CutsceneControlAction action, int tickArg, float speedArg) {
        Network.sendToServer(new RequestCutsceneControlPacket(cutsceneId, action, tickArg, speedArg));
    }

    private static int withActiveCutscene(java.util.function.Consumer<ResourceLocation> action) {
        ResourceLocation id = ClientCutscenePlayer.activeCutsceneId();
        if (id == null) {
            feedback(ChatFormatting.RED, "No cutscene is currently playing.");
            return 0;
        }
        action.accept(id);
        return 1;
    }

    private static void feedback(String message) {
        feedback(ChatFormatting.GRAY, "[Cinematic] " + message);
    }

    private static void feedback(ChatFormatting color, String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message).withStyle(color), false);
        }
    }
}
