package com.dimalab.storymodengine.client.model.animator.expr;

import com.dimalab.storymodengine.common.logging.EngineLog;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

/**
 * Public entry point for evaluating {@link AnimationExpression}s. Compiles each distinct source
 * string once, synchronously, the first time it's evaluated, and caches the result forever — this is
 * a deliberate simplification of HollowEngine's async background-baking (which exists because its
 * language and call volume are both larger); at the scale a handful of expressions evaluated per
 * layer per frame actually needs, a synchronous parse on first use costs nothing measurable, and
 * caching by source string is the one part of HE's strategy actually worth keeping.
 */
public final class AnimExpr {

    private static final ConcurrentHashMap<String, CompiledExpression> CACHE = new ConcurrentHashMap<>();
    private static final KeySetView<String, Boolean> WARNED = ConcurrentHashMap.newKeySet();

    private AnimExpr() {
    }

    public static float evalFloat(AnimationExpression expression, AnimEvalContext context, float defaultValue) {
        String source = expression.source();
        if (source == null || source.isBlank()) {
            return defaultValue;
        }
        return compile(source).eval(context);
    }

    public static boolean evalBool(AnimationExpression expression, AnimEvalContext context, boolean defaultValue) {
        return evalFloat(expression, context, defaultValue ? 1f : 0f) != 0f;
    }

    public static Vector3f evalVector(AnimationVectorExpression expression, AnimEvalContext context) {
        return new Vector3f(
                evalFloat(expression.x(), context, 0f),
                evalFloat(expression.y(), context, 0f),
                evalFloat(expression.z(), context, 0f));
    }

    private static CompiledExpression compile(String source) {
        return CACHE.computeIfAbsent(source, AnimExpr::compileOrFallback);
    }

    /**
     * Literal detection lives here, inside the per-source-string cache, rather than at the top of
     * {@link #evalFloat} — a {@code try/catch} around {@code Float.parseFloat} run on every
     * evaluation means a thrown {@link NumberFormatException} for every non-literal expression, every
     * frame, per bone, per model instance; folding it into {@code computeIfAbsent} means it only ever
     * runs once per distinct source string, matching this class's own stated design ("costs nothing
     * measurable... after first use").
     */
    private static CompiledExpression compileOrFallback(String source) {
        Float literal = tryParseLiteral(source);
        if (literal != null) {
            return new CompiledExpression(new ExprNode.NumberLiteral(literal));
        }
        try {
            return new CompiledExpression(ExpressionParser.parse(source));
        } catch (ExpressionSyntaxException e) {
            if (WARNED.add(source)) {
                EngineLog.channel("Animation").warn("Bad animation expression '{}': {} — treating as 0", source, e.getMessage());
            }
            return new CompiledExpression(new ExprNode.NumberLiteral(0f));
        }
    }

    private static Float tryParseLiteral(String source) {
        try {
            return Float.parseFloat(source);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Test-only: lets {@code debug.ModelSelfTest} assert a malformed expression is only ever warned about once. */
    public static Set<String> warnedSources() {
        return WARNED;
    }
}
