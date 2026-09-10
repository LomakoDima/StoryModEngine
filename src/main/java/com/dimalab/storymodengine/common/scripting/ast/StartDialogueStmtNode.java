package com.dimalab.storymodengine.common.scripting.ast;

/** {@code start dialogue <id>} (also written as a bare {@code dialogue <id>} statement inside a sequence body — both parse to this same node). */
public record StartDialogueStmtNode(SourcePos pos, String dialogueId) implements StmtNode {
}
