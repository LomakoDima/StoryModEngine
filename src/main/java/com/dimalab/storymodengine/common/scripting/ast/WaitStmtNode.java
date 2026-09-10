package com.dimalab.storymodengine.common.scripting.ast;

/** {@code wait N} / {@code wait 2s} — the parser already normalizes an {@code INT} or {@code DURATION} token to a raw tick count, so the compiler only ever sees ticks (compiles straight to {@code Flow.wait(ticks)}). */
public record WaitStmtNode(SourcePos pos, int ticks) implements StmtNode {
}
