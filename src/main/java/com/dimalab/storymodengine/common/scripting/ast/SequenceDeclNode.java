package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code sequence [<id>] { ... }} — {@code id} is {@code null} for an anonymous sequence, only legal directly as a trigger's {@code run} body or a dialogue action/choice body (never as a standalone top-level declaration). */
public record SequenceDeclNode(SourcePos pos, String id, List<MetadataTag> tags, List<StmtNode> body) implements SmeNode {
}
