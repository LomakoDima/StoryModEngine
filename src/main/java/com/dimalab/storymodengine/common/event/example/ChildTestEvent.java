package com.dimalab.storymodengine.common.event.example;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Posted by {@code /sme eventtest parent} to prove parent-event dispatch: a listener
 * declared for {@link PlayerTestEvent} (the priority demo's own listeners) receives this too,
 * alongside a listener declared specifically for {@code ChildTestEvent}.
 */
public final class ChildTestEvent extends PlayerTestEvent {

    public ChildTestEvent(ServerPlayer player, List<String> executionOrder) {
        super(player, executionOrder);
    }
}
