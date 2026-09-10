package com.dimalab.storymodengine.api.entity;

/**
 * What an NPC does when nothing is telling it otherwise.
 *
 * <p>A story NPC is usually a peaceful character, not a monster — HollowEngine's own NPC registers
 * only "float" and "melee attack" and leaves everything else to scripts, because a villager, a guard
 * and a boss are the same entity wearing different behaviour. Making that a value on the entity, and
 * rebuilding the goal list when it changes, is what stops "the mob is hostile" from being compiled in.
 */
public enum NpcBehavior {

    /** Wanders, looks at whoever is nearby, never starts a fight. The default for a story character. */
    PASSIVE,

    /** Wanders and looks around, but hunts the nearest player and strikes with whatever it holds. */
    HOSTILE,

    /** Never moves on its own. A shopkeeper behind a counter, a statue, a character waiting on a script. */
    STATIONARY;

    public static NpcBehavior byOrdinal(int ordinal) {
        NpcBehavior[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PASSIVE;
    }
}
