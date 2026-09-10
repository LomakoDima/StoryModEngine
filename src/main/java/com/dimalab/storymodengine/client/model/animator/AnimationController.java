package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.AnimationPose;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.animator.expr.AnimExpr;
import com.dimalab.storymodengine.common.model.AnimationClip;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A finite state machine over clips: which state the model is in, and the crossfade while it moves to
 * the next — a Java port of HollowEngine's own controller runtime.
 */
public final class AnimationController {

    private AnimationControllerLayerSpec spec;
    private final Map<String, Float> stateTimes = new LinkedHashMap<>();
    private final Map<String, Boolean> stateReversed = new LinkedHashMap<>();
    private Transition transition;
    private String stateId;

    public AnimationController(AnimationControllerLayerSpec initialSpec) {
        this.spec = initialSpec;
    }

    /** Null before the first {@link #sample} call decides an entry state. */
    public String stateId() {
        return stateId;
    }

    /** How far into its own clip the current state is — for {@code query.layer_time}/progress display. */
    public float stateTime() {
        return stateId == null ? 0f : stateTimes.getOrDefault(stateId, 0f);
    }

    /** Updates the controller's rules while retaining playback for states that still exist. */
    public void configure(AnimationControllerLayerSpec next) {
        if (spec.equals(next)) {
            return;
        }
        spec = next;
        Set<String> ids = new HashSet<>();
        for (AnimationControllerStateSpec state : next.states()) {
            ids.add(state.id());
        }
        stateTimes.keySet().retainAll(ids);
        stateReversed.keySet().retainAll(ids);
        if (stateId != null && !ids.contains(stateId)) {
            stateId = null;
            transition = null;
        } else if (transition != null && (!ids.contains(transition.from) || !ids.contains(transition.to))) {
            transition = null;
        }
    }

    /** Null if this controller has no states configured. */
    public AnimationPose sample(PoseTarget target, Set<Integer> allowed, AnimEvalContext context) {
        if (spec.states().isEmpty()) {
            return null;
        }
        if (stateId == null) {
            stateId = spec.entryState() != null ? spec.entryState() : spec.states().get(0).id();
        }
        AnimationControllerStateSpec current = findState(stateId);
        beginTransition(current, context);

        if (transition == null) {
            return sampleState(current, target, allowed, context);
        }

        transition.elapsed += context.deltaTime;
        float factor = transition.duration <= 0f ? 1f : clamp01(transition.elapsed / transition.duration);
        AnimationPose fromPose = sampleState(findState(transition.from), target, allowed, context);
        AnimationPose toPose = sampleState(findState(transition.to), target, allowed, context);

        if (factor >= 1f) {
            stateId = transition.to;
            transition = null;
        }
        return AnimationPose.mix(fromPose, toPose, factor);
    }

    private void beginTransition(AnimationControllerStateSpec current, AnimEvalContext context) {
        if (transition != null) {
            return;
        }
        float currentTime = stateTimes.getOrDefault(current.id(), 0f);
        context.stateTime = currentTime;

        // Highest priority first; equal priority breaks the tie toward the lexicographically smaller
        // target state — this is the one place a naive port of Kotlin's
        // sortedWith(compareByDescending{}.thenBy{}).firstOrNull() is easy to get backwards.
        Comparator<AnimationControllerTransitionSpec> order = Comparator
                .comparingInt(AnimationControllerTransitionSpec::priority).reversed()
                .thenComparing(AnimationControllerTransitionSpec::to);

        // A transition targeting the current state itself is a legitimate, and often the WINNING,
        // candidate — e.g. "run"'s own condition can stay true turn after turn while a lower-priority
        // "walk" (a strict subset: horizontal_speed > 0.02 alone, no sprint check) is simultaneously
        // true too. Excluding self-targets from the candidate pool BEFORE the priority sort — the
        // previous behavior — threw away the correct highest-priority answer ("stay in run") whenever
        // any lower-priority transition's condition also happened to be true, and picked that lower
        // one instead: a real bug, not a hypothetical one, that made "run" continuously crossfade back
        // and forth into "walk" for as long as the player kept sprinting. Self-targets are still
        // filtered — just AFTER the sort, only to skip creating a pointless self-crossfade once the
        // winner is known, never to remove a self-target from contention for winning in the first place.
        AnimationControllerTransitionSpec selected = spec.transitions().stream()
                .filter(t -> t.from().equals(current.id()) || t.from().equals(AnimationControllerTransitionSpec.ANY_STATE))
                .filter(t -> t.exitTime() == null || currentTime >= t.exitTime())
                .filter(t -> AnimExpr.evalBool(t.condition(), context, false))
                .sorted(order)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.to().equals(current.id())) {
            return;
        }

        float duration = Math.max(0f, AnimExpr.evalFloat(selected.duration(), context, 0f));
        transition = new Transition(current.id(), selected.to(), duration);
        stateTimes.put(selected.to(), 0f);
        stateReversed.put(selected.to(), false);
    }

    private AnimationControllerStateSpec findState(String id) {
        for (AnimationControllerStateSpec state : spec.states()) {
            if (state.id().equals(id)) {
                return state;
            }
        }
        return spec.states().get(0);
    }

    private AnimationPose sampleState(AnimationControllerStateSpec state, PoseTarget target, Set<Integer> allowed, AnimEvalContext context) {
        AnimationClip animation = target.animation(state.animation());
        if (animation == null) {
            return new AnimationPose();
        }
        context.stateTime = stateTimes.getOrDefault(state.id(), 0f);
        float speed = AnimExpr.evalFloat(state.speed(), context, 1f);

        float previousTime = stateTimes.getOrDefault(state.id(), 0f);
        boolean previousReversed = stateReversed.getOrDefault(state.id(), false);
        float rawTime = previousTime + speed * context.deltaTime * (previousReversed ? -1f : 1f);
        ClipPlayback.WrappedTime result = ClipPlayback.wrapTime(rawTime, animation.duration(), state.playMode(), previousReversed);
        stateTimes.put(state.id(), result.time());
        stateReversed.put(state.id(), result.reversed());

        return AnimationPose.sample(animation, result.sampleTime(), allowed);
    }

    private static float clamp01(float value) {
        return Math.min(1f, Math.max(0f, value));
    }

    private static final class Transition {
        final String from;
        final String to;
        final float duration;
        float elapsed = 0f;

        Transition(String from, String to, float duration) {
            this.from = from;
            this.to = to;
            this.duration = duration;
        }
    }
}
