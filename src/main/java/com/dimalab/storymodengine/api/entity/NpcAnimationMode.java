package com.dimalab.storymodengine.api.entity;

/**
 * How the clip {@link com.dimalab.storymodengine.common.entity.npc.NpcEntity#animation()} names should
 * play — a plain common-side enum (matching {@link NpcBehavior}'s own placement) so the entity's
 * synced state stays free of {@code client.model.animator.AnimationPlayMode}, which is client-only.
 * {@code client.entity.NpcRenderer} maps each value onto the matching real play mode.
 */
public enum NpcAnimationMode {

    /** Plays once, then the override releases and whatever the NPC would otherwise be doing (its controller, or bind pose) resumes. */
    ONCE,

    /** Plays once, then holds on the last frame — never releases on its own. */
    FREEZE,

    /** Repeats forever until {@code npc_stop_animation} or a new clip replaces it. */
    LOOP;

    public static NpcAnimationMode byOrdinal(int ordinal) {
        NpcAnimationMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LOOP;
    }
}
