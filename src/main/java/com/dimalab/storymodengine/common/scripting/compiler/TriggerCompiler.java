package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.EventNameRegistry;
import com.dimalab.storymodengine.common.scripting.registry.ZoneRegistry;
import com.dimalab.storymodengine.common.trigger.Trigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

/**
 * {@code trigger <id> { when ... run { } }} → {@code Trigger.Builder}. {@code when player enters}
 * resolves a named zone via {@code ZoneRegistry} (SME's grammar has no coordinate literal — a
 * deliberate MVP boundary, see {@code ZoneRegistry}'s own doc). Compiled {@code Trigger}s register
 * into {@code TriggerRegistry} and get armed via {@code TriggerSystem.arm(...)} by {@code
 * SmeCompiler} — mirroring exactly what {@code @AutoTrigger}'s {@code TriggerDiscovery} already
 * does; no trigger JSON loader has ever existed, so this is the only non-annotation path this
 * subsystem has ever had.
 */
public final class TriggerCompiler {

    private TriggerCompiler() {
    }

    public static Trigger compile(TriggerDeclNode node, CompileContext ctx) {
        Trigger.Builder builder = builderFor(node);
        if (node.policy() == TriggerPolicyKind.ONCE) {
            builder.once();
            if (node.persistent()) {
                builder.persistent();
            }
        } else {
            builder.repeat();
        }
        Flow body = new SequenceCompiler(ctx).compile(node.runBody());
        return builder.run(body);
    }

    private static Trigger.Builder builderFor(TriggerDeclNode node) {
        if (node.when() instanceof WhenEntersNode w) {
            ZoneRegistry.Zone zone = ZoneRegistry.get(w.zoneName());
            if (zone == null) {
                EngineLog.channel("SME").error("trigger '{}': unknown zone '{}' (should have failed validation)", node.id(), w.zoneName());
                return Trigger.location(node.id()).radius(net.minecraft.world.phys.Vec3.ZERO, 0).whenEntered();
            }
            return Trigger.location(node.id()).radius(zone.center(), zone.radius()).whenEntered();
        }
        if (node.when() instanceof WhenTimeNode t) {
            return Trigger.time(node.id()).at(t.dayTime());
        }
        if (node.when() instanceof WhenEventNode e) {
            return onEvent(node.id(), e.eventName());
        }
        throw new IllegalStateException("Unknown trigger condition: " + node.when());
    }

    @SuppressWarnings("unchecked")
    private static Trigger.Builder onEvent(String triggerId, String eventName) {
        Class<? extends Event> type = EventNameRegistry.classFor(eventName);
        Function<Event, ServerPlayer> extractor = EventNameRegistry.playerExtractorFor(eventName);
        if (type == null) {
            EngineLog.channel("SME").error("trigger '{}': unknown event name '{}' (should have failed validation)", triggerId, eventName);
            return Trigger.event(triggerId);
        }
        return Trigger.event(triggerId).on((Class<Event>) type, extractor);
    }
}
