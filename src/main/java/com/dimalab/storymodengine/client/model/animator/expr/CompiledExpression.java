package com.dimalab.storymodengine.client.model.animator.expr;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A parsed expression, ready to evaluate against any {@link AnimEvalContext} repeatedly. Everything
 * evaluates to a {@code float} — booleans are {@code 1f}/{@code 0f}, matching real MoLang semantics,
 * so there is no separate boolean AST to keep in sync with this one.
 *
 * <p>Pattern-matching {@code switch} over the sealed {@link ExprNode} hierarchy is a preview feature
 * on Java 17 (stable only from 21) — this project targets 17 without preview features enabled, so
 * evaluation uses plain {@code instanceof} pattern chains instead, which are fully stable since 16.
 */
final class CompiledExpression {

    private final ExprNode root;

    CompiledExpression(ExprNode root) {
        this.root = root;
    }

    float eval(AnimEvalContext context) {
        return evalNode(root, context);
    }

    private static float evalNode(ExprNode node, AnimEvalContext context) {
        if (node instanceof ExprNode.NumberLiteral literal) {
            return literal.value();
        }
        if (node instanceof ExprNode.NamespaceAccess access) {
            return evalNamespace(access, context);
        }
        if (node instanceof ExprNode.Unary unary) {
            float value = evalNode(unary.operand(), context);
            return unary.op() == '!' ? (value != 0f ? 0f : 1f) : -value;
        }
        if (node instanceof ExprNode.Binary binary) {
            return evalBinary(binary, context);
        }
        if (node instanceof ExprNode.Ternary ternary) {
            return evalNode(ternary.condition(), context) != 0f
                    ? evalNode(ternary.ifTrue(), context)
                    : evalNode(ternary.ifFalse(), context);
        }
        if (node instanceof ExprNode.FunctionCall call) {
            return evalFunction(call, context);
        }
        throw new IllegalStateException("unhandled expression node: " + node);
    }

    /**
     * The full {@code math.*} function set (matching HollowEngine's own namespace, reachable bare —
     * see {@code ExpressionParser}'s doc on why there is no {@code math.} prefix requirement),
     * deliberately not extensible from expression source itself (no user-defined functions). Wrong
     * arity or an unknown name both fall back to {@code 0f} rather than throwing: this runs once per
     * layer per frame, and a bad expression must degrade quietly, the same policy already used for an
     * unresolved namespace reference. Trigonometric functions take/return <b>degrees</b>, matching
     * every other angle value already flowing through this context ({@code query.head_x_rotation} and
     * friends are all degrees) — confirmed against HE's own convention, not assumed.
     */
    private static float evalFunction(ExprNode.FunctionCall call, AnimEvalContext context) {
        float[] args = new float[call.args().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = evalNode(call.args().get(i), context);
        }
        return switch (call.name()) {
            case "clamp" -> args.length == 3 ? Math.min(args[2], Math.max(args[1], args[0])) : 0f;
            case "min" -> args.length == 2 ? Math.min(args[0], args[1]) : 0f;
            case "max" -> args.length == 2 ? Math.max(args[0], args[1]) : 0f;
            case "abs" -> args.length == 1 ? Math.abs(args[0]) : 0f;
            case "lerp" -> args.length == 3 ? args[0] + (args[1] - args[0]) * args[2] : 0f;
            case "sin" -> args.length == 1 ? (float) Math.sin(Math.toRadians(args[0])) : 0f;
            case "cos" -> args.length == 1 ? (float) Math.cos(Math.toRadians(args[0])) : 0f;
            case "tan" -> args.length == 1 ? (float) Math.tan(Math.toRadians(args[0])) : 0f;
            case "asin" -> args.length == 1 ? (float) Math.toDegrees(Math.asin(args[0])) : 0f;
            case "acos" -> args.length == 1 ? (float) Math.toDegrees(Math.acos(args[0])) : 0f;
            case "atan" -> args.length == 1 ? (float) Math.toDegrees(Math.atan(args[0])) : 0f;
            case "atan2" -> args.length == 2 ? (float) Math.toDegrees(Math.atan2(args[0], args[1])) : 0f;
            case "sqrt" -> args.length == 1 ? (float) Math.sqrt(args[0]) : 0f;
            case "exp" -> args.length == 1 ? (float) Math.exp(args[0]) : 0f;
            case "ln" -> args.length == 1 ? (float) Math.log(args[0]) : 0f;
            case "floor" -> args.length == 1 ? (float) Math.floor(args[0]) : 0f;
            case "ceil" -> args.length == 1 ? (float) Math.ceil(args[0]) : 0f;
            case "round" -> args.length == 1 ? (float) Math.round(args[0]) : 0f;
            case "trunc" -> args.length == 1 ? (float) (long) args[0] : 0f;
            case "pow" -> args.length == 2 ? (float) Math.pow(args[0], args[1]) : 0f;
            case "mod" -> args.length == 2 && args[1] != 0f ? args[0] - args[1] * (float) Math.floor(args[0] / args[1]) : 0f;
            case "random" -> evalRandom(args);
            default -> 0f;
        };
    }

    /** {@code random()} → [0,1); {@code random(max)} → [0,max); {@code random(min,max)} → [min,max) — a fresh value every evaluation, since nothing in this evaluator caches intermediate results across calls. */
    private static float evalRandom(float[] args) {
        return switch (args.length) {
            case 0 -> ThreadLocalRandom.current().nextFloat();
            case 1 -> ThreadLocalRandom.current().nextFloat() * args[0];
            case 2 -> args[0] + ThreadLocalRandom.current().nextFloat() * (args[1] - args[0]);
            default -> 0f;
        };
    }

    private static float evalNamespace(ExprNode.NamespaceAccess access, AnimEvalContext context) {
        String namespace = access.namespace();
        String key = access.key();
        return switch (namespace) {
            case "query", "q" -> QueryTable.ENTRIES.getOrDefault(key, ctx -> 0f).apply(context);
            case "variable", "v" -> context.variables.getOrDefault(key, 0f);
            case "temp", "t" -> context.temporaries.getOrDefault(key, 0f);
            case "data", "d" -> context.data.getOrDefault(key, 0f);
            default -> 0f; // unresolved namespace -> default, matching HE's warn-with-default policy
        };
    }

    private static float evalBinary(ExprNode.Binary binary, AnimEvalContext context) {
        float left = evalNode(binary.left(), context);
        // Short-circuit && / || before evaluating the right side, matching normal boolean semantics.
        if (binary.op().equals("&&")) {
            return (left != 0f && evalNode(binary.right(), context) != 0f) ? 1f : 0f;
        }
        if (binary.op().equals("||")) {
            return (left != 0f || evalNode(binary.right(), context) != 0f) ? 1f : 0f;
        }
        float right = evalNode(binary.right(), context);
        return switch (binary.op()) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> right != 0f ? left / right : 0f;
            case "==" -> left == right ? 1f : 0f;
            case "!=" -> left != right ? 1f : 0f;
            case "<" -> left < right ? 1f : 0f;
            case ">" -> left > right ? 1f : 0f;
            case "<=" -> left <= right ? 1f : 0f;
            case ">=" -> left >= right ? 1f : 0f;
            default -> throw new IllegalStateException("unhandled operator: " + binary.op());
        };
    }
}
