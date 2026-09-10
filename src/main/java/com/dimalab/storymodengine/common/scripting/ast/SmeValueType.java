package com.dimalab.storymodengine.common.scripting.ast;

/**
 * The closed set of value types SME variables/literals carry — deliberately just these four (no
 * duration, no list, no null): matches this subsystem's own "no complex type system" boundary.
 * {@code wait 2s}'s duration literal is normalized to an {@code int} tick count by the parser and
 * never becomes a value of this type — see {@code WaitStmtNode}.
 */
public enum SmeValueType {
    BOOL,
    INT,
    DOUBLE,
    STRING
}
