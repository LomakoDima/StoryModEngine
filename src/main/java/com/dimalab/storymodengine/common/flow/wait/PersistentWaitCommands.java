package com.dimalab.storymodengine.common.flow.wait;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registered purely for arity/type validation, same reasoning as {@code EntitySearchCommands} — the
 * real suspending behavior is {@code SequenceCompiler}'s special-cased compilation to {@link
 * PersistentWaitTask}, not this method body.
 */
public final class PersistentWaitCommands {

    private PersistentWaitCommands() {
    }

    @StoryCommand("wait_persistent")
    public static void waitPersistent(ServerPlayer player, String name, int ticks) {
        EngineLog.channel("Flow").warn("wait_persistent: only works inside a sequence/trigger run body, not a dialogue action/choice");
    }
}
