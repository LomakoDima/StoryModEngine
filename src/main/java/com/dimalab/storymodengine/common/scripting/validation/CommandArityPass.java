package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.command.ArgumentCoercion;
import com.dimalab.storymodengine.common.scripting.command.StoryCommandRegistry;

import java.util.List;

/**
 * Every {@code command_name arg...} call, checked against {@link StoryCommandRegistry} — unknown
 * name, wrong argument count, or an argument whose literal type can't coerce to the declared Java
 * parameter type. A {@link RefExprNode} argument (a bareword the parser couldn't classify) is only
 * checked against {@code String} parameters here — whether it turns out to be a variable read or a
 * literal string is resolved later, at compile time, by {@code ActionCallCompiler}; either way it
 * always produces a {@code String}-shaped value, so treating it as {@code SmeValueType.STRING} for
 * this dry-run check can never disagree with what compilation actually does.
 */
public final class CommandArityPass {

    private CommandArityPass() {
    }

    public static void run(ValidationContext ctx) {
        StmtWalker.Visitor visitor = new StmtWalker.Visitor() {
            @Override
            public void visitStmt(StmtNode stmt) {
                if (stmt instanceof ActionCallStmtNode call) {
                    check(ctx, call);
                }
            }
        };
        for (SmeNode decl : ctx.story().declarations()) {
            if (decl instanceof TriggerDeclNode t) {
                StmtWalker.walk(t.runBody(), visitor);
            } else if (decl instanceof SequenceDeclNode s) {
                StmtWalker.walk(s.body(), visitor);
            } else if (decl instanceof NpcDeclNode n) {
                StmtWalker.walk(n.onInteract(), visitor);
            } else if (decl instanceof DialogueDeclNode d) {
                for (DialogueNodeNode node : d.nodes()) {
                    for (DialogueEntryNode entry : node.entries()) {
                        if (entry instanceof ActionEntryNode a) {
                            StmtWalker.walk(a.body(), visitor);
                        } else if (entry instanceof ChoiceGroupNode g) {
                            for (ChoiceNode choice : g.options()) {
                                StmtWalker.walk(choice.body(), visitor);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void check(ValidationContext ctx, ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            ctx.error(call.pos(), "SME400", "Unknown story command '" + call.commandName() + "'");
            return;
        }
        List<Class<?>> paramTypes = handle.paramTypes();
        if (call.args().size() != paramTypes.size()) {
            ctx.error(call.pos(), "SME401", "'" + call.commandName() + "' expects " + paramTypes.size()
                    + " argument(s), found " + call.args().size());
            return;
        }
        for (int i = 0; i < paramTypes.size(); i++) {
            SmeValueType argType = typeOf(ctx, call.args().get(i));
            if (argType != null && !ArgumentCoercion.isCoercible(argType, paramTypes.get(i))) {
                ctx.error(call.args().get(i).pos(), "SME402", "Argument " + (i + 1) + " to '" + call.commandName()
                        + "' must be " + paramTypes.get(i).getSimpleName() + ", found " + argType);
            }
        }
    }

    private static SmeValueType typeOf(ValidationContext ctx, ExprNode expr) {
        if (expr instanceof LiteralExprNode lit) {
            return lit.type();
        }
        if (expr instanceof RefExprNode) {
            return SmeValueType.STRING;
        }
        if (expr instanceof VarRefExprNode ref) {
            return ctx.varTypes().get(ref.dottedName());
        }
        return null;
    }
}
