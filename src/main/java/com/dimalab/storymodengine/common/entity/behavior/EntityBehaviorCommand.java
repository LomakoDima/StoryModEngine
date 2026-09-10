package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * {@code /sme entity follow|stopfollow|lookat|stoplookat <target> ...} — {@link
 * GenericBehaviorCommands}'s fast-iteration debug counterpart, same role every other script command's
 * debug sibling already has in this codebase. Single-target ({@code EntityArgument.entity()}), unlike
 * {@code ModelAttachCommand}'s bulk selector — follow/look-at are naturally one-mob-at-a-time.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class EntityBehaviorCommand {

    private EntityBehaviorCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("entity")
                        .then(Commands.literal("follow")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> follow(ctx, EntityArgument.getEntity(ctx, "target"), EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("stopfollow")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> stopFollow(ctx, EntityArgument.getEntity(ctx, "target")))))
                        .then(Commands.literal("lookat")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> lookAt(ctx, EntityArgument.getEntity(ctx, "target"), 16.0F))
                                        .then(Commands.argument("range", FloatArgumentType.floatArg(1.0F, 64.0F))
                                                .executes(ctx -> lookAt(ctx, EntityArgument.getEntity(ctx, "target"), FloatArgumentType.getFloat(ctx, "range"))))))
                        .then(Commands.literal("stoplookat")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> stopLookAt(ctx, EntityArgument.getEntity(ctx, "target")))))));
    }

    private static int follow(CommandContext<CommandSourceStack> context, Entity target, ServerPlayer targetPlayer) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Mob mob = asMob(player, target, "follow");
        if (mob == null) {
            return 0;
        }
        FollowBehavior follow = Capabilities.get(mob, GenericBehaviorCapabilities.FOLLOW);
        if (follow == null) {
            return 0;
        }
        follow.targetId = Optional.of(targetPlayer.getUUID());
        follow.repathCooldown = 0;
        reply(player, mob.getName().getString() + " is now following " + targetPlayer.getGameProfile().getName() + ".");
        return 1;
    }

    private static int stopFollow(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Mob mob = asMob(player, target, "stopfollow");
        if (mob == null) {
            return 0;
        }
        FollowBehavior follow = Capabilities.get(mob, GenericBehaviorCapabilities.FOLLOW);
        if (follow == null) {
            return 0;
        }
        follow.targetId = Optional.empty();
        mob.getNavigation().stop();
        reply(player, mob.getName().getString() + " stopped following.");
        return 1;
    }

    private static int lookAt(CommandContext<CommandSourceStack> context, Entity target, float range) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Mob mob = asMob(player, target, "lookat");
        if (mob == null) {
            return 0;
        }
        LookAtBehavior lookAt = Capabilities.get(mob, GenericBehaviorCapabilities.LOOK_AT);
        if (lookAt == null) {
            return 0;
        }
        lookAt.enabled = true;
        lookAt.range = range;
        reply(player, mob.getName().getString() + " is now watching the nearest living thing within " + range + " blocks.");
        return 1;
    }

    private static int stopLookAt(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Mob mob = asMob(player, target, "stoplookat");
        if (mob == null) {
            return 0;
        }
        LookAtBehavior lookAt = Capabilities.get(mob, GenericBehaviorCapabilities.LOOK_AT);
        if (lookAt == null) {
            return 0;
        }
        lookAt.enabled = false;
        reply(player, mob.getName().getString() + " stopped watching.");
        return 1;
    }

    private static Mob asMob(ServerPlayer player, Entity target, String command) {
        if (!(target instanceof Mob mob)) {
            reply(player, "'" + target.getName().getString() + "' has no navigation/look control to drive (not a Mob) — /sme entity " + command + " needs one.");
            return null;
        }
        return mob;
    }

    private static void reply(ServerPlayer player, String text) {
        EngineLog.channel("EntityBehavior").info(text).toChat(player);
    }
}
