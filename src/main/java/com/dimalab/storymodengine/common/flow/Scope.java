package com.dimalab.storymodengine.common.flow;

/**
 * How widely a {@link Blackboard} variable is shared. Deliberately just these four — the
 * architecture (one {@code Map<String,Object>} per scope inside {@link Blackboard}) allows more to
 * be added later without touching {@link FlowContext}'s API or any {@code Node}.
 */
public enum Scope {
    /** Shared by every Flow in the JVM. */
    GLOBAL,
    /** Shared by every instance of the same {@link FlowDefinition}. */
    FLOW,
    /** Private to one {@link FlowInstance} — the default for most variables. */
    INSTANCE,
    /** Private to one running node, keyed by its own path — the narrowest scope. */
    NODE
}
