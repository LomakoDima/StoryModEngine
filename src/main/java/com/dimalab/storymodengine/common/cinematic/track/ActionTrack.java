package com.dimalab.storymodengine.common.cinematic.track;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Zero or more server-authoritative actions, each fired once on an exact tick — the escape hatch
 * for gameplay effects a cutscene needs to trigger without {@code cinematic} itself hardcoding any
 * gameplay logic (mutate a capability, start a Flow, spawn an effect — the caller decides, this
 * just guarantees the "once, on this tick" timing). Deliberately server-side only, walked from
 * {@code CinematicManager#tick()} and never from the client: the actions this exists for
 * (capability writes, starting a Flow) are all server-authoritative by nature, and keeping this
 * off the client avoids yet another place needing the "don't trust the client" discipline the rest
 * of this engine already applies. A cutscene that only needs a client-local visual side effect
 * should reach for {@code EventTrack} instead.
 */
public final class ActionTrack {

    private final List<ActionCue> cues;

    public ActionTrack(List<ActionCue> cues) {
        this.cues = List.copyOf(cues);
    }

    public List<ActionCue> cues() {
        return cues;
    }

    /** One scheduled server-side action — fires once, exactly on {@link #tick()}. */
    public record ActionCue(int tick, Consumer<ServerPlayer> action) {
    }
}
