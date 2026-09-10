package com.dimalab.storymodengine.common.scripting.ast;

/** The root of every SME AST node — sealed so a compiler/validator switch over the full tree is exhaustive at compile time, matching {@code dialogue.DialogueEntry}'s own established convention in this codebase. */
public sealed interface SmeNode
        permits StoryNode, MetadataTag, StmtNode, ExprNode,
        DialogueDeclNode, DialogueNodeNode, DialogueEntryNode, ChoiceNode,
        QuestDeclNode, ObjectiveNode, RewardNode,
        TriggerDeclNode, TriggerConditionNode,
        SequenceDeclNode, IncludeNode, NpcDeclNode, ShopDeclNode {

    SourcePos pos();
}
