package com.dimalab.storymodengine.api.math.interp;

/**
 * Reshapes a linear parameter {@code t ∈ [0, 1]} into an eased one — pure {@code float → float},
 * with no notion of the value it will end up blending. Attaches to any {@link Interpolator} via
 * {@link Interpolator#eased(Easing)}, so the same easing curve works whether it's shaping a
 * camera's position along a spline, a UI widget's slide-in, or a light's color fade.
 *
 * <p>The standard Penner-style set below is deliberately just easing curves — nothing about
 * splines, vectors, or ticks belongs in this file; that separation is the point (see
 * {@code ARCHITECTURE.md}).
 */
@FunctionalInterface
public interface Easing {

    float apply(float t);

    Easing LINEAR = t -> t;

    Easing EASE_IN_QUAD = t -> t * t;
    Easing EASE_OUT_QUAD = t -> 1 - (1 - t) * (1 - t);
    Easing EASE_IN_OUT_QUAD = t -> t < 0.5f ? 2 * t * t : 1 - pow2(-2 * t + 2) / 2;

    Easing EASE_IN_CUBIC = t -> t * t * t;
    Easing EASE_OUT_CUBIC = t -> 1 - pow3(1 - t);
    Easing EASE_IN_OUT_CUBIC = t -> t < 0.5f ? 4 * t * t * t : 1 - pow3(-2 * t + 2) / 2;

    Easing EASE_IN_QUART = t -> t * t * t * t;
    Easing EASE_OUT_QUART = t -> 1 - pow4(1 - t);
    Easing EASE_IN_OUT_QUART = t -> t < 0.5f ? 8 * pow4(t) : 1 - pow4(-2 * t + 2) / 2;

    Easing EASE_IN_QUINT = t -> pow5(t);
    Easing EASE_OUT_QUINT = t -> 1 - pow5(1 - t);
    Easing EASE_IN_OUT_QUINT = t -> t < 0.5f ? 16 * pow5(t) : 1 - pow5(-2 * t + 2) / 2;

    Easing EASE_IN_SINE = t -> 1 - (float) Math.cos(t * Math.PI / 2);
    Easing EASE_OUT_SINE = t -> (float) Math.sin(t * Math.PI / 2);
    Easing EASE_IN_OUT_SINE = t -> -(((float) Math.cos(Math.PI * t) - 1) / 2);

    Easing EASE_IN_EXPO = t -> t <= 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
    Easing EASE_OUT_EXPO = t -> t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
    Easing EASE_IN_OUT_EXPO = t -> {
        if (t <= 0) return 0f;
        if (t >= 1) return 1f;
        return t < 0.5f
                ? (float) Math.pow(2, 20 * t - 10) / 2
                : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
    };

    Easing EASE_IN_CIRC = t -> 1 - (float) Math.sqrt(1 - pow2(t));
    Easing EASE_OUT_CIRC = t -> (float) Math.sqrt(1 - pow2(t - 1));
    Easing EASE_IN_OUT_CIRC = t -> t < 0.5f
            ? (1 - (float) Math.sqrt(1 - pow2(2 * t))) / 2
            : ((float) Math.sqrt(1 - pow2(-2 * t + 2)) + 1) / 2;

    Easing EASE_IN_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return c3 * t * t * t - c1 * t * t;
    };
    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * pow3(t - 1) + c1 * pow2(t - 1);
    };
    Easing EASE_IN_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c2 = c1 * 1.525f;
        return t < 0.5f
                ? (pow2(2 * t) * ((c2 + 1) * 2 * t - c2)) / 2
                : (pow2(2 * t - 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
    };

    Easing EASE_IN_ELASTIC = t -> {
        if (t <= 0) return 0f;
        if (t >= 1) return 1f;
        float c4 = (float) (2 * Math.PI / 3);
        return -(float) Math.pow(2, 10 * t - 10) * (float) Math.sin((t * 10 - 10.75f) * c4);
    };
    Easing EASE_OUT_ELASTIC = t -> {
        if (t <= 0) return 0f;
        if (t >= 1) return 1f;
        float c4 = (float) (2 * Math.PI / 3);
        return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75f) * c4) + 1;
    };

    Easing EASE_OUT_BOUNCE = Easing::outBounce;
    Easing EASE_IN_BOUNCE = t -> 1 - outBounce(1 - t);

    private static float outBounce(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    }

    private static float pow2(float v) {
        return v * v;
    }

    private static float pow3(float v) {
        return v * v * v;
    }

    private static float pow4(float v) {
        return v * v * v * v;
    }

    private static float pow5(float v) {
        return v * v * v * v * v;
    }

    /** {@code this} run forward then backward — a symmetric "there and back" shape over {@code [0, 1]}. */
    default Easing mirrored() {
        return t -> t < 0.5f ? apply(2 * t) : apply(2 * (1 - t));
    }

    /** {@code this} run in reverse: {@code t -> 1 - this(1 - t)}. */
    default Easing reversed() {
        return t -> 1 - apply(1 - t);
    }

}
