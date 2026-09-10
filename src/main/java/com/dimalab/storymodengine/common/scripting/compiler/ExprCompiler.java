package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.persistence.StoryVariableStore;

/**
 * {@link ExprNode} → a real {@link Evaluator} closure tree, built once at compile time — no
 * interpreter loop ever runs at runtime. {@link #compileBoolean} is the one method both {@code
 * IfStmtNode}/{@code RaycastIfStmtNode} branching and a dialogue choice's {@code when (expr)} →
 * {@code DialogueDefinition.Builder.condition(Evaluator<Boolean>)} go through (both take a plain
 * {@code Evaluator<Boolean>} running on {@code FlowContext} — verified by reading {@code
 * DialogueDefinition.java}, dialogue conditions are not a separate {@code DialogueContext}-flavored
 * type).
 */
public final class ExprCompiler {

    private final CompileContext ctx;

    public ExprCompiler(CompileContext ctx) {
        this.ctx = ctx;
    }

    public Evaluator<Boolean> compileBoolean(ExprNode expr) {
        if (expr instanceof LiteralExprNode lit && lit.type() == SmeValueType.BOOL) {
            boolean v = (Boolean) lit.value();
            return fc -> v;
        }
        if (expr instanceof VarRefExprNode ref) {
            String name = ref.dottedName();
            return fc -> StoryVariableStore.getBool(fc.player(), name);
        }
        if (expr instanceof UnaryExprNode u && u.op() == UnaryOp.NOT) {
            Evaluator<Boolean> inner = compileBoolean(u.operand());
            return fc -> !inner.evaluate(fc);
        }
        if (expr instanceof BinaryExprNode b) {
            if (b.op() == BinaryOp.AND) {
                Evaluator<Boolean> left = compileBoolean(b.left());
                Evaluator<Boolean> right = compileBoolean(b.right());
                return fc -> left.evaluate(fc) && right.evaluate(fc);
            }
            if (b.op() == BinaryOp.OR) {
                Evaluator<Boolean> left = compileBoolean(b.left());
                Evaluator<Boolean> right = compileBoolean(b.right());
                return fc -> left.evaluate(fc) || right.evaluate(fc);
            }
            return compileComparison(b);
        }
        EngineLog.channel("SME").warn("ExprCompiler: expected a boolean expression, got {} — treating as false",
                expr.getClass().getSimpleName());
        return fc -> false;
    }

    private Evaluator<Boolean> compileComparison(BinaryExprNode b) {
        Evaluator<Object> left = compileValue(b.left());
        Evaluator<Object> right = compileValue(b.right());
        BinaryOp op = b.op();
        return fc -> compareValues(left.evaluate(fc), right.evaluate(fc), op);
    }

    private static boolean compareValues(Object lv, Object rv, BinaryOp op) {
        if (lv instanceof Boolean || rv instanceof Boolean) {
            boolean l = truthy(lv);
            boolean r = truthy(rv);
            return op == BinaryOp.NEQ ? l != r : l == r;
        }
        if (lv instanceof String || rv instanceof String) {
            int cmp = String.valueOf(lv).compareTo(String.valueOf(rv));
            return applyOrdering(cmp, op);
        }
        double l = ((Number) lv).doubleValue();
        double r = ((Number) rv).doubleValue();
        return applyOrdering(Double.compare(l, r), op);
    }

    private static boolean applyOrdering(int cmp, BinaryOp op) {
        return switch (op) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case GT -> cmp > 0;
            case LT -> cmp < 0;
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
            default -> false;
        };
    }

    private static boolean truthy(Object v) {
        return v instanceof Boolean b ? b : v != null;
    }

    /** Compiles a value-producing expression (a literal or variable read) — used for comparison operands and {@code set} statement values. Unary/binary sub-expressions here are boolean-shaped and evaluated through {@link #compileBoolean}. */
    public Evaluator<Object> compileValue(ExprNode expr) {
        if (expr instanceof LiteralExprNode lit) {
            Object v = lit.value();
            return fc -> v;
        }
        if (expr instanceof VarRefExprNode ref) {
            String name = ref.dottedName();
            SmeValueType type = ctx.typeOf(name);
            return fc -> StoryVariableStore.get(fc.player(), name, type).asObject();
        }
        if (expr instanceof RefExprNode ref) {
            String literal = ref.id();
            return fc -> literal;
        }
        if (expr instanceof UnaryExprNode || expr instanceof BinaryExprNode) {
            Evaluator<Boolean> bool = compileBoolean(expr);
            return fc -> bool.evaluate(fc);
        }
        EngineLog.channel("SME").warn("ExprCompiler: unsupported value expression {}", expr.getClass().getSimpleName());
        return fc -> null;
    }
}
