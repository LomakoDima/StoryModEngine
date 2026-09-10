package com.dimalab.storymodengine.common.entity.search;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code find_nearest_entity}/{@code await_entity} — registered here purely so {@code
 * StoryCommandRegistry}/{@code ArgumentCoercion} know their arity and parameter types (needed by
 * {@code SequenceCompiler.compileOne}'s special-casing, mirroring {@code npc_move_to}'s own
 * {@code compileAwaitableMoveTo} precedent) and so compile-time validation accepts the call at all.
 * Binding a result into a script variable is something a plain {@code void @StoryCommand} cannot do,
 * so all the real behavior lives in the compiler special case, not these method bodies — called
 * directly (e.g. from a dialogue action/choice body, which never reaches that special-casing) they
 * just warn, the same pattern {@code EntityDataCommands}'s own {@code entity_get_data_*} uses.
 */
public final class EntitySearchCommands {

    private EntitySearchCommands() {
    }

    @StoryCommand("find_nearest_entity")
    public static void findNearestEntity(ServerPlayer player, String resultVar, String entityTypeId, double radius) {
        warnIfCalledDirectly("find_nearest_entity");
    }

    @StoryCommand("await_entity")
    public static void awaitEntity(ServerPlayer player, String targetVar, int timeoutTicks) {
        warnIfCalledDirectly("await_entity");
    }

    private static void warnIfCalledDirectly(String command) {
        EngineLog.channel("EntitySearch").warn(
                "{}: only works inside a sequence/trigger run body, not a dialogue action/choice", command);
    }
}
