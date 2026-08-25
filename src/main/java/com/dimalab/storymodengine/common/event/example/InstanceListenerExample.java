package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * Demonstrates an instance listener (task section 8/25.5) — never discovered automatically (only
 * {@code static @SubscribeEvent} methods are), so {@code EventTestCommand} registers one instance
 * of this explicitly, once, via {@code Events.register(new InstanceListenerExample())}.
 */
public final class InstanceListenerExample {

    private int timesHeard;

    @SubscribeEvent
    public void onInstanceTest(InstanceTestEvent event) {
        timesHeard++;
        EngineLog.channel("Events").success(
                "Instance listener heard InstanceTestEvent (heard {} time(s) total)", timesHeard).toChat(event.player());
    }
}
