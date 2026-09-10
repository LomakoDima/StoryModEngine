package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.entity.EntityTargeting;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * {@code entity_follow_player}/{@code entity_stop_follow}/{@code entity_look_at_nearest}/{@code
 * entity_stop_look_at} — the script-facing side of {@link GenericBehaviorSystem}: attach follow/look-at
 * behavior to <b>any</b> entity a script can name, not just an NPC. {@code target} accepts either an
 * NPC's own script name or a bare UUID (what {@code find_nearest_entity} binds into a variable) via the
 * existing {@link EntityTargeting#resolve}, unchanged — the same "a bad script call costs that call, not
 * the story" discipline every other command in this codebase already follows: a target that doesn't
 * resolve, or resolves to something that isn't a {@link Mob} (no navigation/look control to drive),
 * warns and returns rather than throwing.
 */
public final class GenericBehaviorCommands {

    private GenericBehaviorCommands() {
    }

    @StoryCommand("entity_follow_player")
    public static void entityFollowPlayer(ServerPlayer player, String target, String targetPlayerName) {
        Mob mob = resolveMob(player, target, "entity_follow_player");
        if (mob == null) {
            return;
        }
        Player targetPlayer = player.getServer().getPlayerList().getPlayerByName(targetPlayerName);
        if (targetPlayer == null) {
            EngineLog.channel("EntityBehavior").warn("entity_follow_player: no player named '{}' online", targetPlayerName);
            return;
        }
        FollowBehavior follow = Capabilities.get(mob, GenericBehaviorCapabilities.FOLLOW);
        if (follow == null) {
            return;
        }
        follow.targetId = Optional.of(targetPlayer.getUUID());
        follow.repathCooldown = 0;
    }

    @StoryCommand("entity_stop_follow")
    public static void entityStopFollow(ServerPlayer player, String target) {
        Mob mob = resolveMob(player, target, "entity_stop_follow");
        if (mob == null) {
            return;
        }
        FollowBehavior follow = Capabilities.get(mob, GenericBehaviorCapabilities.FOLLOW);
        if (follow == null) {
            return;
        }
        follow.targetId = Optional.empty();
        mob.getNavigation().stop();
    }

    @StoryCommand("entity_look_at_nearest")
    public static void entityLookAtNearest(ServerPlayer player, String target, float range) {
        Mob mob = resolveMob(player, target, "entity_look_at_nearest");
        if (mob == null) {
            return;
        }
        LookAtBehavior lookAt = Capabilities.get(mob, GenericBehaviorCapabilities.LOOK_AT);
        if (lookAt == null) {
            return;
        }
        lookAt.enabled = true;
        lookAt.range = range;
    }

    @StoryCommand("entity_stop_look_at")
    public static void entityStopLookAt(ServerPlayer player, String target) {
        Mob mob = resolveMob(player, target, "entity_stop_look_at");
        if (mob == null) {
            return;
        }
        LookAtBehavior lookAt = Capabilities.get(mob, GenericBehaviorCapabilities.LOOK_AT);
        if (lookAt == null) {
            return;
        }
        lookAt.enabled = false;
    }

    private static Mob resolveMob(ServerPlayer player, String target, String command) {
        Entity entity = EntityTargeting.resolve(player.serverLevel(), target);
        if (entity == null) {
            EngineLog.channel("EntityBehavior").warn("{}: no entity matches target '{}'", command, target);
            return null;
        }
        if (!(entity instanceof Mob mob)) {
            EngineLog.channel("EntityBehavior").warn("{}: '{}' has no navigation/look control to drive (not a Mob)", command, target);
            return null;
        }
        return mob;
    }
}
