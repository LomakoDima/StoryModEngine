package com.dimalab.storymodengine.common.cinematic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * The per-play bindings a {@link CutsceneDefinition} needs resolved before it can be applied to
 * the real world — the same role {@code FlowContext} plays for {@code Flow}. Built fresh for every
 * play (client-side, from the fields of {@code PlayCutscenePacket}); a definition never hardcodes
 * any of this, since the same definition is replayed with different bindings every time.
 */
public final class CutsceneContext {

    private final Level level;
    private final Player viewer;
    private final Map<String, Integer> actorEntityIds;

    public CutsceneContext(Level level, Player viewer, Map<String, Integer> actorEntityIds) {
        this.level = level;
        this.viewer = viewer;
        this.actorEntityIds = Map.copyOf(actorEntityIds);
    }

    public Level level() {
        return level;
    }

    /** The player watching this cutscene — whose camera gets overridden. */
    public Player viewer() {
        return viewer;
    }

    /** The entity id bound to symbolic actor name {@code key}, or {@code null} if none was supplied. */
    public Integer actorEntityId(String key) {
        return actorEntityIds.get(key);
    }
}
