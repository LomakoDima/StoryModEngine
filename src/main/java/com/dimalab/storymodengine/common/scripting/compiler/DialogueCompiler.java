package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.dialogue.DialogueCommand;
import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code dialogue <id> { ... }} → {@code DialogueDefinition.Builder}. Re-verified directly against
 * {@code DialogueDefinition.java}: {@code .condition(...)}/{@code .command(...)}/{@code .gotoNode(...)}
 * are three independently-settable fields on the same pending choice — no engine change was needed
 * to support a choice body with arbitrary effects plus a trailing jump, contrary to what might be
 * assumed from the requirements text alone.
 *
 * <p>A choice body ending in {@code end} (rather than {@code jump <id>}) needs a real node id to
 * {@code .gotoNode(...)}, since that call takes a node id, not an end sentinel — this compiler emits
 * a synthetic {@code __sme_end_N} node containing only {@code .end()}, exactly the shape a
 * hand-written {@code DialogueDefinition} would use for "this branch just ends here." Compiler-
 * internal bookkeeping, not a builder API change.
 */
public final class DialogueCompiler {

    private final ExprCompiler exprCompiler;
    private final EffectCompiler effectCompiler;
    private final List<String> syntheticEndNodes = new ArrayList<>();
    private int endCounter;

    public DialogueCompiler(CompileContext ctx) {
        this.exprCompiler = new ExprCompiler(ctx);
        this.effectCompiler = new EffectCompiler(ctx);
    }

    public DialogueDefinition compile(DialogueDeclNode decl) {
        syntheticEndNodes.clear();
        endCounter = 0;

        DialogueDefinition.Builder builder = DialogueDefinition.builder(ScriptingRegistryFacade.storyId(decl.id()));
        for (DialogueNodeNode node : decl.nodes()) {
            builder.node(node.nodeId());
            for (DialogueEntryNode entry : node.entries()) {
                compileEntry(builder, entry);
            }
        }
        for (String synthetic : syntheticEndNodes) {
            builder.node(synthetic).end();
        }
        return builder.build();
    }

    private void compileEntry(DialogueDefinition.Builder builder, DialogueEntryNode entry) {
        if (entry instanceof LineEntryNode line) {
            builder.line(line.speaker(), line.text());
        } else if (entry instanceof ActionEntryNode action) {
            Consumer<FlowContext> effect = effectCompiler.compile(action.body());
            builder.action(DialogueCommand.of(dctx -> effect.accept(dctx.flowContext())));
        } else if (entry instanceof GateEntryNode gate) {
            builder.gate(exprCompiler.compileBoolean(gate.condition()));
        } else if (entry instanceof JumpEntryNode jump) {
            builder.jump(jump.targetNodeId());
        } else if (entry instanceof EndEntryNode) {
            builder.end();
        } else if (entry instanceof ChoiceGroupNode group) {
            for (ChoiceNode choice : group.options()) {
                compileChoice(builder, choice);
            }
        }
    }

    private void compileChoice(DialogueDefinition.Builder builder, ChoiceNode choice) {
        builder.choice(choice.text());
        if (choice.condition() != null) {
            builder.condition(exprCompiler.compileBoolean(choice.condition()));
        }

        List<StmtNode> body = choice.body();
        String trailingJumpTarget = null;
        boolean trailingEnd = false;
        List<StmtNode> effectBody = body;
        if (!body.isEmpty()) {
            StmtNode last = body.get(body.size() - 1);
            if (last instanceof JumpStmtNode j) {
                trailingJumpTarget = j.targetId();
                effectBody = body.subList(0, body.size() - 1);
            } else if (last instanceof EndStmtNode) {
                trailingEnd = true;
                effectBody = body.subList(0, body.size() - 1);
            }
        }

        if (!effectBody.isEmpty()) {
            Consumer<FlowContext> effect = effectCompiler.compile(effectBody);
            builder.command(DialogueCommand.of(dctx -> effect.accept(dctx.flowContext())));
        }

        if (trailingJumpTarget != null) {
            builder.gotoNode(trailingJumpTarget);
        } else if (trailingEnd) {
            String synthetic = "__sme_end_" + (endCounter++);
            syntheticEndNodes.add(synthetic);
            builder.gotoNode(synthetic);
        }
        // Neither: falls through to whatever entry comes next in this node — matches gotoNode's own
        // null-target ("no jump") semantics exactly.
    }
}
