package com.dimalab.storymodengine.common.scripting.ast;

/** {@code wait event x.y} — {@code eventName} is resolved against {@code EventNameRegistry} at validation/compile time, never a raw Java class name. */
public record WaitEventStmtNode(SourcePos pos, String eventName) implements StmtNode {
}
