package com.dimalab.storymodengine.common.flow;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The bridge between a running Flow and the outside world — deliberately small. A {@code Node}'s
 * lambda gets a {@link #player()}/{@link #level()} and calls the engine's own existing facades
 * directly for everything else (e.g. {@code Capabilities.get(context.player(), ...)},
 * {@code Network.sendToPlayer(context.player(), ...)}) — {@code FlowContext} doesn't wrap or
 * re-expose those, it just carries enough identity for a Node to use them itself. No Forge
 * implementation type (Forge {@code LazyOptional}, {@code SimpleChannel}, {@code
 * AttachCapabilitiesEvent}) is reachable from here.
 *
 * <p>{@link #getVariable}/{@link #setVariable} are the {@link Blackboard} access point — see its
 * Javadoc for the explicit line drawn between it and {@code Capabilities} (execution-state variable
 * vs. persistent game state). {@link #put}/{@link #get} remain as plain aliases for {@code
 * Scope.INSTANCE} — the shape this class had before {@link Blackboard} existed — kept for API
 * stability, not because anything in this engine still calls them directly.
 */
public final class FlowContext {

    private final ServerPlayer player;
    private final Blackboard blackboard;

    public FlowContext(ServerPlayer player, ResourceLocation flowId) {
        this.player = player;
        this.blackboard = new Blackboard(flowId);
    }

    public ServerPlayer player() {
        return player;
    }

    public Level level() {
        return player.level();
    }

    /** Convenience passthrough to {@code EngineLog.channel("Flow")} — nodes rarely need more than this. */
    public void log(String message, Object... args) {
        EngineLog.channel("Flow").debug(message, args);
    }

    public <T> T getVariable(Scope scope, String key) {
        return blackboard.get(scope, key);
    }

    public <T> void setVariable(Scope scope, String key, T value) {
        blackboard.set(scope, key, value);
    }

    public void put(String key, Object value) {
        setVariable(Scope.INSTANCE, key, value);
    }

    public <T> T get(String key) {
        return getVariable(Scope.INSTANCE, key);
    }
}
