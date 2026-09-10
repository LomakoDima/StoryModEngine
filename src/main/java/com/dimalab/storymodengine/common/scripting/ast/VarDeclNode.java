package com.dimalab.storymodengine.common.scripting.ast;

/**
 * {@code var name = value} — declares a story variable's default value; compiles to a one-time
 * "ensure this key defaults to X" write, never a direct value at datapack-load time (see {@code
 * VariableLowering}). Implements {@link StmtNode} (not {@code SmeNode} directly) since a {@code var}
 * declaration is legal both as a top-level story declaration and inside a block.
 */
public record VarDeclNode(SourcePos pos, String name, LiteralExprNode initial) implements StmtNode {
}
