package com.dimalab.storymodengine.client.model.animator;

/**
 * Where playback of one clip currently is, and which way it's going — a faithful port of
 * HollowEngine's own {@code ClipPlayback}/{@code wrapTime} state machine. Shared verbatim by {@link
 * com.dimalab.storymodengine.client.model.ClipLayer} (one instance per clip layer) and {@link
 * AnimationController} (one {@link WrappedTime} computation per state, driven by that state's own
 * remembered time/reversed pair rather than an instance of this class — see its {@code advance}
 * method, which duplicates this class's {@code advance} body against per-state maps instead of
 * per-instance fields).
 */
public final class ClipPlayback {

    private float time = 0f;
    private boolean reversed = false;
    private boolean ended = false;
    private float endElapsed = 0f;

    public float time() {
        return time;
    }

    public boolean ended() {
        return ended;
    }

    /** Seconds spent past the end of a one-shot ({@link AnimationPlayMode#ONCE}) clip — what its fade-out is measured against. */
    public float endElapsed() {
        return endElapsed;
    }

    /** Advances by {@code deltaTime} seconds at {@code speed}× and returns the time to actually sample the clip at. */
    public float advance(float duration, AnimationPlayMode playMode, float speed, float deltaTime) {
        if (duration <= 0f) {
            return 0f;
        }
        if (ended && playMode == AnimationPlayMode.ONCE) {
            endElapsed += Math.max(0f, deltaTime) * Math.abs(speed);
            return duration;
        }
        float rawTime = time + speed * deltaTime * (reversed ? -1f : 1f);
        WrappedTime result = wrapTime(rawTime, duration, playMode, reversed);
        time = result.time();
        reversed = result.reversed();
        ended = result.ended();
        endElapsed = (playMode == AnimationPlayMode.ONCE && result.ended()) ? Math.max(0f, rawTime - duration) : 0f;
        return result.sampleTime();
    }

    public record WrappedTime(float time, float sampleTime, boolean reversed, boolean ended) {
    }

    /**
     * Pure state-transition function: given a raw (possibly out-of-range) time, how {@code playMode}
     * wraps it into a valid sample time, and whether playback direction flips or the clip ends.
     * {@link AnimationController} calls this directly (not through an instance of this class) since it
     * tracks time/reversed per-state rather than per-layer.
     */
    public static WrappedTime wrapTime(float time, float duration, AnimationPlayMode playMode, boolean reversed) {
        return switch (playMode) {
            case ONCE -> {
                float clamped = clamp(time, 0f, duration);
                yield new WrappedTime(clamped, clamped, false, time >= duration);
            }
            case LOOP -> {
                float wrapped = modPositive(time, duration);
                yield new WrappedTime(wrapped, wrapped, false, false);
            }
            case CLAMP_FOREVER -> {
                float clamped = clamp(time, 0f, duration);
                yield new WrappedTime(clamped, clamped, false, false);
            }
            case PING_PONG -> {
                float t = time;
                boolean r = reversed;
                // A while loop, not an if: a delta large enough to overshoot by more than one full
                // duration must reflect more than once, exactly like HollowEngine's own version.
                while (t < 0f || t > duration) {
                    if (t > duration) {
                        t = duration - (t - duration);
                        r = !r;
                    } else {
                        t = -t;
                        r = !r;
                    }
                }
                yield new WrappedTime(t, t, r, false);
            }
        };
    }

    private static float modPositive(float value, float divisor) {
        return ((value % divisor) + divisor) % divisor;
    }

    private static float clamp(float value, float lo, float hi) {
        return Math.min(hi, Math.max(lo, value));
    }
}
