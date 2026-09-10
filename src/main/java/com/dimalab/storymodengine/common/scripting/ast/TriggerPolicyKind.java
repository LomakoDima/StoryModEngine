package com.dimalab.storymodengine.common.scripting.ast;

/** {@code once} / {@code repeat} on a {@code trigger} — {@code REPEAT} is the grammar's default when neither keyword is written, matching {@code Trigger.Builder}'s own default. */
public enum TriggerPolicyKind {
    ONCE,
    REPEAT
}
