package com.dimalab.storymodengine.common.scripting.ast;

/** One statement inside a {@code block} — usable inside a sequence body, a trigger's {@code run} body, or a dialogue action/choice body; sealed so {@code SequenceCompiler}'s per-statement switch is exhaustive. */
public sealed interface StmtNode extends SmeNode
        permits SetStmtNode, VarDeclNode, IfStmtNode, WaitStmtNode, WaitEventStmtNode, JumpStmtNode,
        CallStmtNode, ReturnStmtNode, EndStmtNode, ActionCallStmtNode, StartQuestStmtNode,
        PlayCinematicStmtNode, StartDialogueStmtNode, RaycastIfStmtNode {
}
