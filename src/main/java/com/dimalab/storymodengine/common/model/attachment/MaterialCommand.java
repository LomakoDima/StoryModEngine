package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * {@code /sme material set|setskin|reset|resetall <targets> ...} — {@link MaterialCommands}'s
 * fast-iteration debug counterpart, same role every other script command's debug sibling already has.
 * Bulk selector ({@code EntityArgument.entities()}), like {@code ModelAttachCommand} — unlike the
 * single-target follow/look-at debug commands, retexturing several entities at once (a whole group of
 * guards, say) is a real use case.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class MaterialCommand {

    private MaterialCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("material")
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .then(Commands.argument("texture", StringArgumentType.string())
                                                        .executes(ctx -> set(ctx,
                                                                EntityArgument.getEntities(ctx, "targets"),
                                                                StringArgumentType.getString(ctx, "material"),
                                                                StringArgumentType.getString(ctx, "texture")))))))
                        .then(Commands.literal("setskin")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .then(Commands.argument("player", StringArgumentType.string())
                                                        .executes(ctx -> setSkin(ctx,
                                                                EntityArgument.getEntities(ctx, "targets"),
                                                                StringArgumentType.getString(ctx, "material"),
                                                                StringArgumentType.getString(ctx, "player")))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .executes(ctx -> reset(ctx,
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "material"))))))
                        .then(Commands.literal("resetall")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> resetAll(ctx, EntityArgument.getEntities(ctx, "targets")))))));
    }

    private static int set(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets, String material, String texture) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (ResourceLocation.tryParse(texture) == null) {
            reply(player, "'" + texture + "' is not a valid resource location.");
            return 0;
        }
        int count = 0;
        for (Entity target : targets) {
            MaterialOverrides overrides = Capabilities.get(target, MaterialOverrideCapabilities.OVERRIDES);
            if (overrides == null) {
                continue;
            }
            MaterialOverride override = new MaterialOverride();
            override.texture = texture;
            overrides.byName.put(material, override);
            sync(target);
            count++;
        }
        reply(player, "Set '" + material + "' to '" + texture + "' on " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static int setSkin(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets, String material, String player_) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = 0;
        for (Entity target : targets) {
            MaterialOverrides overrides = Capabilities.get(target, MaterialOverrideCapabilities.OVERRIDES);
            if (overrides == null) {
                continue;
            }
            MaterialOverride override = new MaterialOverride();
            override.skinPlayer = player_;
            overrides.byName.put(material, override);
            sync(target);
            count++;
        }
        reply(player, "Set '" + material + "' to " + player_ + "'s skin on " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets, String material) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = 0;
        for (Entity target : targets) {
            MaterialOverrides overrides = Capabilities.get(target, MaterialOverrideCapabilities.OVERRIDES);
            if (overrides == null || overrides.byName.remove(material) == null) {
                continue;
            }
            sync(target);
            count++;
        }
        reply(player, "Reset '" + material + "' on " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = 0;
        for (Entity target : targets) {
            MaterialOverrides overrides = Capabilities.get(target, MaterialOverrideCapabilities.OVERRIDES);
            if (overrides == null || overrides.byName.isEmpty()) {
                continue;
            }
            overrides.byName.clear();
            sync(target);
            count++;
        }
        reply(player, "Reset every material override on " + count + " entit" + (count == 1 ? "y" : "ies") + ".");
        return count;
    }

    private static void sync(Entity entity) {
        Capabilities.markDirty(entity, MaterialOverrideCapabilities.OVERRIDES);
        MaterialOverrideCapabilities.OVERRIDES.sync(entity);
    }

    private static void reply(ServerPlayer player, String text) {
        EngineLog.channel("Model").info(text).toChat(player);
    }
}
