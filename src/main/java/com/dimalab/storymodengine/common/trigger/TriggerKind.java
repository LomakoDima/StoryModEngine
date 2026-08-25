package com.dimalab.storymodengine.common.trigger;

/** Which detection mechanism a {@link Trigger} uses — set by which of {@link Trigger#location}/{@link Trigger#time}/{@link Trigger#event} built it. */
public enum TriggerKind {
    LOCATION, TIME, EVENT
}
