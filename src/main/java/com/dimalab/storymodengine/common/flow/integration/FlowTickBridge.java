package com.dimalab.storymodengine.common.flow.integration;

import com.dimalab.storymodengine.common.flow.FlowManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place Flow touches a genuine Minecraft tick — everything in {@code flow}'s core
 * (including {@code Node#tick}) is otherwise completely agnostic to what drives it (see {@code
 * Node}'s Javadoc). Registers idempotently on {@code MinecraftForge.EVENT_BUS} (the same {@code
 * AtomicBoolean}-guarded pattern {@code CapabilityLifecycle}/{@code MinecraftEventBridge} already
 * use), listening only to {@code Phase.END} so one server tick drives exactly one
 * {@code FlowManager.tick()} call, not two.
 */
public final class FlowTickBridge {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private FlowTickBridge() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(FlowTickBridge.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FlowManager.tick();
        }
    }
}
