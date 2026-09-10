package com.dimalab.storymodengine.common.scripting.ast;

/** {@code end} — inside a dialogue body this ends the dialogue outright (see {@code DialogueCompiler}); inside a sequence/trigger body it's a documented no-op, kept only for symmetry. */
public record EndStmtNode(SourcePos pos) implements StmtNode {
}
