package com.dimalab.storymodengine.common.concurrent;

import com.dimalab.storymodengine.common.concurrent.integration.AsyncLifecycle;
import com.dimalab.storymodengine.common.concurrent.integration.ConcurrencyTickBridge;

/**
 * Registers this subsystem's two Forge listeners and creates nothing — the real thread pools come
 * later, from {@link AsyncLifecycle} on {@code ServerStartingEvent} (see that class's Javadoc for
 * why). Because this has zero dependencies on any other subsystem, {@code EngineBootstrap} calls it
 * first, before anything that might itself want to use {@code Async}.
 */
public final class ConcurrencyBootstrap {

    private ConcurrencyBootstrap() {
    }

    public static void init() {
        AsyncLifecycle.registerOnce();
        ConcurrencyTickBridge.registerOnce();
    }
}
