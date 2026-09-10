package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * {@code /sme model attach|clear <targets> [model]} — the generic path: any entity in the world,
 * not just an NPC or the dedicated prop entity, can be told to wear a glTF model. Server-side, same
 * reasoning as {@code PropCommand}/{@code NpcCommand}: a vanilla mob only actually looks different
 * once the server agrees to it, since the client-side render hook ({@code
 * mixin.client.LivingEntityRendererMixin}) only fires for an entity that carries this data.
 *
 * <p>Unlike {@code NpcCommand}/{@code PropCommand} (both "the nearest thing I just spawned"), this
 * uses a real target selector ({@code EntityArgument.entities()}) — "any entity" is the whole point
 * here, not a convenience shortcut for a freshly-spawned one.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class ModelAttachCommand {

    private ModelAttachCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("model")
                        .then(Commands.literal("attach")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("model", StringArgumentType.word())
                                                .executes(ctx -> attach(ctx,
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "model"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> clear(ctx, EntityArgument.getEntities(ctx, "targets")))))));
    }

    private static int attach(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets, String model) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        ModelDefinition definition = ModelPhysics.resolve(model);
        if (definition == null) {
            reply(player, "No model named '" + model + "' — it must exist at assets/storymodengine/storymodengine/models/" + model + ".gltf (or .glb) inside the mod.");
            return 0;
        }

        int count = 0;
        for (Entity target : targets) {
            ModelAttachment attachment = Capabilities.get(target, ModelAttachCapabilities.MODEL_ATTACHMENT);
            if (attachment == null) {
                continue;
            }
            attachment.modelName = model;
            Capabilities.markDirty(target, ModelAttachCapabilities.MODEL_ATTACHMENT);
            ModelAttachCapabilities.MODEL_ATTACHMENT.sync(target);
            count++;
        }

        reply(player, "Attached '" + model + "' to " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        int count = 0;
        for (Entity target : targets) {
            ModelAttachment attachment = Capabilities.get(target, ModelAttachCapabilities.MODEL_ATTACHMENT);
            if (attachment == null || attachment.modelName.isEmpty()) {
                continue;
            }
            attachment.modelName = "";
            Capabilities.markDirty(target, ModelAttachCapabilities.MODEL_ATTACHMENT);
            ModelAttachCapabilities.MODEL_ATTACHMENT.sync(target);
            count++;
        }

        reply(player, "Cleared the attached model from " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static void reply(ServerPlayer player, String text) {
        EngineLog.channel("Model").info(text).toChat(player);
    }
}
