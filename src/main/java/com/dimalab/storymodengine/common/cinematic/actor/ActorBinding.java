package com.dimalab.storymodengine.common.cinematic.actor;

import com.dimalab.storymodengine.common.cinematic.CutsceneContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * How an {@code ActorTrack} finds the real {@link Entity} it should visually drive — kept separate
 * from the track itself so a {@code CutsceneDefinition} never hardcodes a specific runtime entity
 * id (which changes every time a cutscene is played). One implementation exists today, {@link
 * #named}, resolved through the per-play {@link CutsceneContext}; the interface leaves room for
 * {@code player()}/{@code entity(UUID)}/other binding kinds later without touching {@code
 * ActorTrack}.
 */
@FunctionalInterface
public interface ActorBinding {

    /** Resolves to the entity this binding currently points at, or {@code null} if it can't be resolved. */
    Entity resolve(Level level, CutsceneContext context);

    /** Resolves via {@link CutsceneContext#actorEntityId(String)} — the id supplied when the cutscene was started. */
    static ActorBinding named(String key) {
        return (level, context) -> {
            Integer id = context.actorEntityId(key);
            return id == null ? null : level.getEntity(id);
        };
    }
}
