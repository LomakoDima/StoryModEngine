package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The priority/parent-dispatch demo event for {@code /sme eventtest priority|parent} —
 * a plain class (not a {@code record}, since {@link ChildTestEvent} needs to {@code extends} it;
 * records can only implement interfaces). {@link #executionOrder()} is a mutable log every listener
 * of one {@code post} call appends its own name to, read back by the command after {@code post}
 * returns to print the actual dispatch order to chat.
 */
public class PlayerTestEvent implements Event {

    private final ServerPlayer player;
    private final List<String> executionOrder;

    public PlayerTestEvent(ServerPlayer player, List<String> executionOrder) {
        this.player = player;
        this.executionOrder = executionOrder;
    }

    public ServerPlayer player() {
        return player;
    }

    public List<String> executionOrder() {
        return executionOrder;
    }
}
