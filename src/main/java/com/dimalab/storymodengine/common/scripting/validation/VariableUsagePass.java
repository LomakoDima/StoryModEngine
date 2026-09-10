package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;

import java.util.List;

/** Every {@code var}/{@code set}/variable-read in a file, checked against one flat same-file symbol table (cross-file variable checking is out of MVP scope — a variable is a runtime capability key, not a compiled reference, so an undeclared cross-file read fails soft at runtime rather than needing a hard compile-time guarantee). */
public final class VariableUsagePass {

    private VariableUsagePass() {
    }

    public static void run(ValidationContext ctx) {
        // Pass 1: collect every var declared anywhere in this file (top-level and nested) first, so
        // a read/set earlier in file order than its declaration still resolves — matches how a
        // dialogue's own forward jumps already work.
        collectDecls(ctx, ctx.story().declarations());

        for (SmeNode decl : ctx.story().declarations()) {
            if (decl instanceof DialogueDeclNode d) {
                checkDialogue(ctx, d);
            } else if (decl instanceof TriggerDeclNode t) {
                StmtWalker.walk(t.runBody(), visitorFor(ctx));
            } else if (decl instanceof SequenceDeclNode s) {
                StmtWalker.walk(s.body(), visitorFor(ctx));
            } else if (decl instanceof NpcDeclNode n) {
                StmtWalker.walk(n.onInteract(), visitorFor(ctx));
            }
        }
    }

    private static void collectDecls(ValidationContext ctx, List<SmeNode> decls) {
        for (SmeNode decl : decls) {
            if (decl instanceof VarDeclNode v) {
                declare(ctx, v);
            } else if (decl instanceof TriggerDeclNode t) {
                collectDeclsInStmts(ctx, t.runBody());
            } else if (decl instanceof SequenceDeclNode s) {
                collectDeclsInStmts(ctx, s.body());
            } else if (decl instanceof NpcDeclNode n) {
                collectDeclsInStmts(ctx, n.onInteract());
            } else if (decl instanceof DialogueDeclNode d) {
                for (DialogueNodeNode node : d.nodes()) {
                    for (DialogueEntryNode entry : node.entries()) {
                        if (entry instanceof ActionEntryNode a) {
                            collectDeclsInStmts(ctx, a.body());
                        } else if (entry instanceof ChoiceGroupNode g) {
                            for (ChoiceNode choice : g.options()) {
                                collectDeclsInStmts(ctx, choice.body());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void collectDeclsInStmts(ValidationContext ctx, List<StmtNode> stmts) {
        StmtWalker.walk(stmts, new StmtWalker.Visitor() {
            @Override
            public void visitStmt(StmtNode stmt) {
                if (stmt instanceof VarDeclNode v) {
                    declare(ctx, v);
                }
            }
        });
    }

    private static void declare(ValidationContext ctx, VarDeclNode v) {
        if (ctx.varTypes().containsKey(v.name())) {
            ctx.error(v.pos(), "SME202", "Variable '" + v.name() + "' declared more than once");
            return;
        }
        ctx.varTypes().put(v.name(), v.initial().type());
    }

    private static void checkDialogue(ValidationContext ctx, DialogueDeclNode d) {
        for (DialogueNodeNode node : d.nodes()) {
            for (DialogueEntryNode entry : node.entries()) {
                if (entry instanceof ActionEntryNode a) {
                    StmtWalker.walk(a.body(), visitorFor(ctx));
                } else if (entry instanceof GateEntryNode g) {
                    checkExprVars(ctx, g.condition());
                } else if (entry instanceof ChoiceGroupNode group) {
                    for (ChoiceNode choice : group.options()) {
                        if (choice.condition() != null) {
                            checkExprVars(ctx, choice.condition());
                        }
                        StmtWalker.walk(choice.body(), visitorFor(ctx));
                    }
                }
            }
        }
    }

    private static void checkExprVars(ValidationContext ctx, ExprNode expr) {
        if (expr instanceof VarRefExprNode ref) {
            checkRead(ctx, ref);
        } else if (expr instanceof UnaryExprNode u) {
            checkExprVars(ctx, u.operand());
        } else if (expr instanceof BinaryExprNode b) {
            checkExprVars(ctx, b.left());
            checkExprVars(ctx, b.right());
        }
    }

    private static StmtWalker.Visitor visitorFor(ValidationContext ctx) {
        return new StmtWalker.Visitor() {
            @Override
            public void visitStmt(StmtNode stmt) {
                if (stmt instanceof SetStmtNode set) {
                    checkSet(ctx, set);
                }
            }

            @Override
            public void visitExpr(ExprNode expr) {
                if (expr instanceof VarRefExprNode ref) {
                    checkRead(ctx, ref);
                }
            }
        };
    }

    private static void checkRead(ValidationContext ctx, VarRefExprNode ref) {
        if (!ctx.varTypes().containsKey(ref.dottedName())) {
            ctx.error(ref.pos(), "SME203", "Read of undeclared variable '" + ref.dottedName() + "'");
        }
    }

    private static void checkSet(ValidationContext ctx, SetStmtNode set) {
        var type = ctx.varTypes().get(set.varName());
        if (type == null) {
            ctx.error(set.pos(), "SME204", "Assignment to undeclared variable '" + set.varName() + "' — declare it with 'var' first");
            return;
        }
        if (set.op() != SetOp.ASSIGN && (type == SmeValueType.BOOL || type == SmeValueType.STRING)) {
            ctx.error(set.pos(), "SME205", "'+='/'-=' is not valid on " + type + " variable '" + set.varName() + "'");
        }
    }
}
