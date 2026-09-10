package com.dimalab.storymodengine.common.entity.data;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.entity.EntityTargeting;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@code entity_set_data_*}/{@code entity_get_data_*}/{@code entity_get_or_set_data_string} — a
 * generic per-entity key/value store reachable from {@code .sme}, closing the {@code entity.data}
 * half of the "three levels of persistence" gap (see {@code sme-vs-he.html}). Persistence itself is
 * automatic — {@link EntityDataNever}/{@link EntityDataOwner}/{@link EntityDataTracking} are plain
 * {@code @Capability} data, so every write here survives a save/reload with no extra plumbing, the
 * same way {@code ModelAttachment} already does.
 *
 * <p>{@code target} accepts either an NPC's own script name or a bare UUID string (see
 * {@link EntityTargeting}) — the latter is how a value bound by {@code find_nearest_entity} (a UUID
 * stored in a plain {@code STRING} script variable, since that's the only carrier this engine's
 * closed value-type set offers) flows back in as a target for these commands.
 *
 * <p>The {@code entity_get_data_*}/{@code entity_get_or_set_data_string} methods below only ever run
 * their real logic when compiled inside a {@code sequence}/{@code trigger} body — {@code
 * SequenceCompiler} special-cases these three command names (mirroring {@code npc_move_to}'s own
 * {@code compileAwaitableMoveTo} precedent) because binding a result into a script variable is
 * something a plain {@code void @StoryCommand} cannot do on its own. Called from a dialogue
 * action/choice body instead (which never reaches that special-casing), they just warn — there is no
 * meaningful fire-and-forget version of "read a value into a variable."
 */
public final class EntityDataCommands {

    private EntityDataCommands() {
    }

    @StoryCommand("entity_set_data_string")
    public static void setString(ServerPlayer player, String target, String key, String value, String syncMode) {
        Entity entity = resolve(player, target, "entity_set_data_string");
        if (entity == null) {
            return;
        }
        CompoundTag tag = tagOrWarn(entity, syncMode, "entity_set_data_string");
        if (tag == null) {
            return;
        }
        tag.putString(key, value);
        EntityDataAccess.markAndSync(entity, syncMode);
    }

    @StoryCommand("entity_set_data_int")
    public static void setInt(ServerPlayer player, String target, String key, int value, String syncMode) {
        Entity entity = resolve(player, target, "entity_set_data_int");
        if (entity == null) {
            return;
        }
        CompoundTag tag = tagOrWarn(entity, syncMode, "entity_set_data_int");
        if (tag == null) {
            return;
        }
        tag.putInt(key, value);
        EntityDataAccess.markAndSync(entity, syncMode);
    }

    @StoryCommand("entity_get_data_string")
    public static void getString(ServerPlayer player, String resultVar, String target, String key) {
        warnIfCalledDirectly("entity_get_data_string");
    }

    @StoryCommand("entity_get_data_int")
    public static void getInt(ServerPlayer player, String resultVar, String target, String key) {
        warnIfCalledDirectly("entity_get_data_int");
    }

    @StoryCommand("entity_get_or_set_data_string")
    public static void getOrSetString(ServerPlayer player, String resultVar, String target, String key, String defaultValue) {
        warnIfCalledDirectly("entity_get_or_set_data_string");
    }

    private static Entity resolve(ServerPlayer player, String target, String command) {
        Entity entity = EntityTargeting.resolve(player.serverLevel(), target);
        if (entity == null) {
            EngineLog.channel("EntityData").warn("{}: no entity matches target '{}'", command, target);
        }
        return entity;
    }

    private static CompoundTag tagOrWarn(Entity entity, String syncMode, String command) {
        CompoundTag tag = EntityDataAccess.tagFor(entity, syncMode);
        if (tag == null) {
            EngineLog.channel("EntityData").warn("{}: unknown sync mode '{}' (expected never/owner/tracking)", command, syncMode);
        }
        return tag;
    }

    private static void warnIfCalledDirectly(String command) {
        EngineLog.channel("EntityData").warn(
                "{}: only works inside a sequence/trigger run body, not a dialogue action/choice — binding a result needs the compiler's special-cased handling", command);
    }
}
