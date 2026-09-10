package com.dimalab.storymodengine.common.scripting.ast;

/** {@code return} — early-exits the enclosing sequence/trigger-run body; compiles to a failing {@code Flow.actionResult}, since a failed step already stops the rest of a {@code Flow.sequence}. */
public record ReturnStmtNode(SourcePos pos) implements StmtNode {
}
