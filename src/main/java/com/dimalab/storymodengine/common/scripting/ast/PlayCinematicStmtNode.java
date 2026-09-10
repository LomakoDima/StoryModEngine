package com.dimalab.storymodengine.common.scripting.ast;

/** {@code play cinematic <id>} — MVP cinematic support is reference-only (an already-defined cutscene, Java- or JSON-authored); full {@code cinematic <id> { ... }} authoring is out of scope for this pass. */
public record PlayCinematicStmtNode(SourcePos pos, String cutsceneId) implements StmtNode {
}
