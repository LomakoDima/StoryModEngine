package com.dimalab.storymodengine.common.cinematic.integration;

import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one place the server-side half of {@code cinematic} touches a genuine Minecraft tick —
 * mirrors {@code flow.integration.FlowTickBridge} exactly, down to the {@code Phase.END} guard so
 * one server tick drives exactly one {@code CinematicManager.tick()} call.
 */
public final class CinematicTickBridge {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CinematicTickBridge() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(CinematicTickBridge.class);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CinematicManager.tick();
        }
    }
}
