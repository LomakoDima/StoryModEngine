package com.dimalab.storymodengine.api.entity;

/**
 * How solid an NPC is. Borrowed as an idea from HollowEngine's own {@code HitboxMode}: a story NPC
 * is sometimes a person you bump into, sometimes scenery you walk through, and which one it is
 * belongs to the NPC rather than to its entity type.
 */
public enum NpcHitboxMode {

    /** Ordinary mob physics: shoves and is shoved, but you can walk through it like any villager. */
    PUSHABLE,

    /** You physically stop against it, the way a boat or shulker stops you. It is not shoved around. */
    SOLID,

    /**
     * No collision at all — walk straight through. For an NPC that is set dressing, or one mid-cutscene
     * that must not block the player.
     */
    GHOST;

    public static NpcHitboxMode byOrdinal(int ordinal) {
        NpcHitboxMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PUSHABLE;
    }
}
