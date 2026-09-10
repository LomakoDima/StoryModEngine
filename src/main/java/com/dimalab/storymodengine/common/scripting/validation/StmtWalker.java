package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;

import java.util.List;

/**
 * Shared recursive walk over a {@code List<StmtNode>} body (a sequence/trigger-run/dialogue-action/
 * choice body) — used by every validation pass that needs to visit every statement/expression
 * reachable from one, so the traversal logic exists exactly once.
 *
 * <p>Written with {@code instanceof} pattern chains rather than a pattern-matching {@code switch} —
 * the project targets Java 17 (no {@code --enable-preview}), where switch patterns are still a
 * preview feature (finalized only in Java 21); plain {@code instanceof} pattern matching has been
 * final since Java 16 and is used throughout instead.
 */
public final class StmtWalker {

    private StmtWalker() {
    }

    public interface Visitor {
        default void visitStmt(StmtNode stmt) {
        }

        default void visitExpr(ExprNode expr) {
        }
    }

    public static void walk(List<StmtNode> stmts, Visitor visitor) {
        for (StmtNode stmt : stmts) {
            visitor.visitStmt(stmt);
            if (stmt instanceof SetStmtNode s) {
                walkExpr(s.value(), visitor);
            } else if (stmt instanceof VarDeclNode v) {
                walkExpr(v.initial(), visitor);
            } else if (stmt instanceof IfStmtNode i) {
                walkExpr(i.cond(), visitor);
                walk(i.thenBlock(), visitor);
                for (ElseIfBranch b : i.elseIfs()) {
                    walkExpr(b.cond(), visitor);
                    walk(b.block(), visitor);
                }
                walk(i.elseBlock(), visitor);
            } else if (stmt instanceof RaycastIfStmtNode r) {
                walk(r.thenBlock(), visitor);
                walk(r.elseBlock(), visitor);
            } else if (stmt instanceof CallStmtNode c) {
                c.args().forEach(a -> walkExpr(a, visitor));
            } else if (stmt instanceof ActionCallStmtNode a) {
                a.args().forEach(arg -> walkExpr(arg, visitor));
            }
            // WaitStmtNode/WaitEventStmtNode/JumpStmtNode/ReturnStmtNode/EndStmtNode/
            // StartQuestStmtNode/PlayCinematicStmtNode/StartDialogueStmtNode carry no nested
            // expressions or statements — visitStmt above already saw them, nothing more to walk.
        }
    }

    private static void walkExpr(ExprNode expr, Visitor visitor) {
        visitor.visitExpr(expr);
        if (expr instanceof UnaryExprNode u) {
            walkExpr(u.operand(), visitor);
        } else if (expr instanceof BinaryExprNode b) {
            walkExpr(b.left(), visitor);
            walkExpr(b.right(), visitor);
        }
        // LiteralExprNode/VarRefExprNode/RefExprNode are leaves — nothing nested to walk.
    }
}
