package com.dimalab.storymodengine.common.scripting.ast;

/** {@code jump <id>} — inside a dialogue node body this targets a sibling node id; at sequence/trigger scope it targets any dialogue/quest/cutscene/sequence id, resolved by priority order (see {@code SequenceCompiler}). */
public record JumpStmtNode(SourcePos pos, String targetId) implements StmtNode {
}
