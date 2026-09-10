package com.dimalab.storymodengine.common.scripting.command;

import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;

/**
 * The closed value-type ↔ Java-parameter-type coercion table {@code @StoryCommand} arguments go
 * through — deliberately small and closed (not a general reflective marshaller), matching this
 * subsystem's "no complex type system" boundary. {@link #isCoercible} is used by both {@code
 * validation.CommandArityPass} (a dry-run check against a literal's declared type) and {@code
 * compiler.ActionCallCompiler} (compiling the runtime argument-resolution closures) — the two can
 * never disagree about what's coercible, since both call this same method.
 */
public final class ArgumentCoercion {

    private ArgumentCoercion() {
    }

    public static boolean isCoercible(SmeValueType from, Class<?> to) {
        return switch (from) {
            case BOOL -> to == boolean.class || to == Boolean.class;
            case INT -> to == int.class || to == Integer.class || to == long.class || to == Long.class
                    || to == double.class || to == Double.class;
            case DOUBLE -> to == double.class || to == Double.class;
            case STRING -> to == String.class;
        };
    }

    /** Converts an already-evaluated runtime value ({@code Boolean}/{@code Long}/{@code Double}/{@code String}) to {@code to} — only ever called after {@link #isCoercible} already confirmed the shape is valid. */
    public static Object coerce(Object value, Class<?> to) {
        if (to == String.class) {
            return String.valueOf(value);
        }
        if (to == boolean.class || to == Boolean.class) {
            return value;
        }
        if (to == int.class || to == Integer.class) {
            return ((Number) value).intValue();
        }
        if (to == long.class || to == Long.class) {
            return ((Number) value).longValue();
        }
        if (to == double.class || to == Double.class) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Unsupported @StoryCommand coercion target: " + to);
    }
}
