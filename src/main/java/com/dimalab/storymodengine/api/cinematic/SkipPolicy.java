package com.dimalab.storymodengine.api.cinematic;

/**
 * How {@code CutsceneRuntime#skip()} is allowed to behave for one {@code CutsceneDefinition} — an
 * authoring-time property (like duration), not a runtime one. Defaults to {@link #SKIPPABLE} on
 * {@code CutsceneDefinition.Builder}, so every existing definition (which never set this) keeps
 * behaving exactly as before. No UI is implied by any of these — only the runtime/API foundation a
 * mod's own "press to skip" prompt would call into.
 */
public enum SkipPolicy {

    /** {@code skip()} jumps straight to the last tick. */
    SKIPPABLE,

    /** {@code skip()} is a no-op, logged at {@code debug} — not an error, since a mod may legitimately gate skipping by its own UI instead. */
    NON_SKIPPABLE,

    /** Same behavior as {@link #SKIPPABLE} — its own named policy for callers that want to be explicit about intent rather than relying on the default. */
    SKIP_TO_END,

    /** {@code skip()} jumps to the definition's configured default-skip marker, falling back to {@link #SKIP_TO_END} if none was set. */
    SKIP_TO_MARKER
}
