package com.dimalab.storymodengine.common.scripting.ast;

/** {@code start quest <id>}. */
public record StartQuestStmtNode(SourcePos pos, String questId) implements StmtNode {
}
