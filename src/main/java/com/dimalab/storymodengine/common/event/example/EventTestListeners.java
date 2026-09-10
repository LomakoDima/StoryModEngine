package com.dimalab.storymodengine.common.event.example;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.capabilities.example.ModCapabilities;
import com.dimalab.storymodengine.common.capabilities.example.StoryPlayerData;
import com.dimalab.storymodengine.api.event.EventPriority;
import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.logging.EngineLog;

/**
 * Every {@code static} listener {@code /sme eventtest} exercises — discovered
 * automatically by {@code EventListenerDiscovery} at mod startup; nothing in this class, or
 * anywhere else, ever calls {@code EventBus.register} on it. Each demo posts its event, then reads
 * the event's own mutable {@code executionOrder} log back to print the real dispatch order to chat
 * — proof, not narration.
 */
@SuppressWarnings("unused")
public final class EventTestListeners {

    private EventTestListeners() {
    }

    // --- priority demo (also fires for the parent demo's ChildTestEvent, since it extends PlayerTestEvent) ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHighest(PlayerTestEvent event) {
        event.executionOrder().add("HIGHEST");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerHigh(PlayerTestEvent event) {
        event.executionOrder().add("HIGH");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerNormal(PlayerTestEvent event) {
        event.executionOrder().add("NORMAL");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerLow(PlayerTestEvent event) {
        event.executionOrder().add("LOW");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLowest(PlayerTestEvent event) {
        event.executionOrder().add("LOWEST");
    }

    // --- parent-dispatch demo: only fires for ChildTestEvent, never a plain PlayerTestEvent ---

    @SubscribeEvent
    public static void onChildOnly(ChildTestEvent event) {
        event.executionOrder().add("CHILD_ONLY");
    }

    // --- cancellation demo: HIGH cancels, NORMAL/LOW must never run ---

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCancelHigh(CancellableTestEvent event) {
        event.executionOrder().add("HIGH");
        event.setCancelled(true);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onCancelNormal(CancellableTestEvent event) {
        event.executionOrder().add("NORMAL");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCancelLow(CancellableTestEvent event) {
        event.executionOrder().add("LOW");
    }

    // --- exception-isolation demo: B throws, A and C must still run. Distinct priorities keep the
    //     order deterministic regardless of reflection scan order (never rely on that — see EventBus). ---

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExceptionA(ExceptionTestEvent event) {
        event.executionOrder().add("A");
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onExceptionB(ExceptionTestEvent event) {
        event.executionOrder().add("B");
        throw new RuntimeException("Deliberate test exception from listener B");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExceptionC(ExceptionTestEvent event) {
        event.executionOrder().add("C");
    }

    // --- network + capability + math demo: triggered by EventTestPacket#handle, not by a command directly ---

    @SubscribeEvent
    public static void onNetworkTriggered(NetworkTriggeredTestEvent event) {
        StoryPlayerData data = Capabilities.get(event.player(), ModCapabilities.STORY_DATA);
        data.storyPoints += 1;
        Capabilities.markDirty(event.player(), ModCapabilities.STORY_DATA);
        ModCapabilities.STORY_DATA.sync(event.player());

        EngineLog.channel("Events").success(
                "Network-triggered event received at ({}, {}, {}) — Story Points: {}",
                event.position().x, event.position().y, event.position().z, data.storyPoints).toChat(event.player());
    }
}
