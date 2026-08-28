package com.dimalab.storymodengine.common.concurrent.integration;

import com.dimalab.storymodengine.common.concurrent.executor.AsyncExecutors;
import com.dimalab.storymodengine.common.concurrent.executor.PlatformThreadExecutorFactory;
import com.dimalab.storymodengine.common.concurrent.schedule.TickScheduler;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates and tears down the real thread pools around one Minecraft server lifetime — deliberately
 * on {@code ServerStartingEvent}/{@code ServerStoppingEvent}, not at mod-construction time, because
 * both events fire on the *integrated* (single-player) server too: creating pools once at bootstrap
 * would leave a single-player world reload with dead, already-shut-down executors. Registered via
 * the same {@code AtomicBoolean REGISTERED} + {@code MinecraftForge.EVENT_BUS.register(Class)} idiom
 * {@code flow.integration.FlowTickBridge}/{@code cinematic.integration.CinematicTickBridge}/{@code
 * capabilities.CapabilityLifecycle} already use — not {@code @Mod.EventBusSubscriber}, since engine
 * code isn't tied to one modid.
 */
public final class AsyncLifecycle {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private AsyncLifecycle() {
    }

    public static void registerOnce() {
        if (REGISTERED.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.register(AsyncLifecycle.class);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        PlatformThreadExecutorFactory factory = new PlatformThreadExecutorFactory();
        AsyncExecutors executors = new AsyncExecutors();
        executors.start(factory);
        AsyncExecutors.installAsCurrent(executors);
        EngineLog.channel("Async").info("Executors started ({}).", factory.describe());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AsyncExecutors.current().shutdown();
        FlowResumeQueue.clear();
        TickScheduler.clear();
    }
}
