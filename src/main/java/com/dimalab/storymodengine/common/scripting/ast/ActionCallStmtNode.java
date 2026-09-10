package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/** {@code command_name "a" 5} — dispatches through {@code StoryCommandRegistry}; a leading bare {@code player} argument token is dropped by the parser (implicit self-target, see {@code ActionCallCompiler}), so {@code args} never contains one. */
public record ActionCallStmtNode(SourcePos pos, String commandName, List<ExprNode> args) implements StmtNode {
}
