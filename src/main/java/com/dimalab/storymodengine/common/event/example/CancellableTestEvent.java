package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.CancellableEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Posted by {@code /storymodengine eventtest cancel} — a HIGH-priority listener cancels it; NORMAL/LOW must never run. */
public final class CancellableTestEvent implements CancellableEvent {

    private final ServerPlayer player;
    private final List<String> executionOrder;
    private boolean cancelled;

    public CancellableTestEvent(ServerPlayer player, List<String> executionOrder) {
        this.player = player;
        this.executionOrder = executionOrder;
    }

    public ServerPlayer player() {
        return player;
    }

    public List<String> executionOrder() {
        return executionOrder;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
