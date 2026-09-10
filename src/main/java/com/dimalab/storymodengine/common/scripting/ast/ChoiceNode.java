package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/**
 * One option inside a {@link ChoiceGroupNode} — {@code condition} is nullable ({@code when (expr)}
 * is optional). {@code body} is an arbitrary statement list, not just a jump target: {@code
 * DialogueCompiler} compiles every non-control-flow statement into one {@code DialogueCommand.of(...)}
 * and any trailing {@code jump}/{@code end} into a {@code .gotoNode(...)} call — see its class doc.
 */
public record ChoiceNode(SourcePos pos, String text, ExprNode condition, List<StmtNode> body) implements SmeNode {
}
