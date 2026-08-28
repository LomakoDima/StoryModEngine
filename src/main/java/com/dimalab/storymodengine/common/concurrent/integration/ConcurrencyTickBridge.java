package com.dimalab.storymodengine.common.concurrent.integration;

import com.dimalab.storymodengine.common.concurrent.schedule.TickScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors {@code flow.integration.FlowTickBridge} byte-for-byte except for one deliberate
 * difference: this listens at {@code Phase.START}, not {@code Phase.END}. That ordering is load-
 * bearing — draining {@link FlowResumeQueue} here means any {@code Node} state it changes settles
 * strictly *before* {@code FlowTickBridge}'s {@code Phase.END} handler runs {@code
 * FlowManager.tick()}'s before/after comparison, so a background completion is always observed
 * within the same tick. {@link TickScheduler#tick()} runs right after, for the same reason — its due
 * entries may themselves resolve an {@code AsyncTask} a {@code flow.node.AsyncNode} is waiting on.
 */
public final class ConcurrencyTickBridge {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private ConcurrencyTickBridge() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(ConcurrencyTickBridge.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            FlowResumeQueue.drain();
            TickScheduler.tick();
        }
    }
}
