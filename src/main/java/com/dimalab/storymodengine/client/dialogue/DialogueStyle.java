package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueMode;
import com.dimalab.storymodengine.common.dialogue.DialogueWindowAnimation;
import com.dimalab.storymodengine.api.dialogue.TextRevealMode;

/**
 * Every visual/animation/layout knob {@link DialogueWindowRenderer} reads — client-only, never sent
 * over the network (each client renders with whatever style it's given locally, the same way a
 * resource pack is local). {@link #DEFAULT} is a plain, usable-out-of-the-box look; a mod author
 * passes a different instance to {@link DialogueWindow} for a custom theme — nothing about the
 * runtime (server, {@code DialogueRunner}'s compiled {@code Flow}) knows or cares which style is in
 * use. {@code choiceColor}/{@code selectedChoiceColor} are read by {@link DialogueScreen} only —
 * kept here rather than split out, since choices weren't in scope to restructure this round.
 *
 * <p>Background alpha (~{@code 0x80}, roughly 50%) and a fully transparent {@code borderColor} by
 * default are tuned to match a flat, borderless "black glass" overlay — deliberately not the
 * heavier ~78% opacity + visible border this looked like earlier, matching the reference visual
 * this pass was built against. A mod author who wants a visible border just picks a style with a
 * non-transparent {@code borderColor}; the draw call was never removed, only defaulted off.
 *
 * <p>{@link #forMode(DialogueMode)} resolves one named constant per {@link DialogueMode} — color
 * and default-animation variants only, never a different layout, so this stays a small lookup table
 * rather than a second styling engine. Actual per-mode *text* styling (italic/bold/scale) lives in
 * {@link DialogueWindowRenderer#renderText}, since {@code Style}/{@code PoseStack} flags aren't
 * colors this record could hold generically.
 */
public record DialogueStyle(
        int backgroundColor,
        int borderColor,
        int speakerColor,
        int textColor,
        int choiceColor,
        int selectedChoiceColor,
        TextRevealMode revealMode,
        int charactersPerTick,
        DialogueWindowAnimation animation,
        int animationDurationMs,
        DialogueLayout layout
) {
    private static final int NO_BORDER = 0x00FFFFFF;

    public static final DialogueStyle DEFAULT = new DialogueStyle(
            0x80000000, NO_BORDER, 0xFFFFD700, 0xFFE0E0E0, 0xFFAAAAAA, 0xFFFFFF55,
            TextRevealMode.TYPEWRITER, 2, DialogueWindowAnimation.FADE, 250, DialogueLayout.DEFAULT);

    /** Dim, cool-tinted — a thought isn't spoken aloud. Slightly slower reveal. */
    public static final DialogueStyle THOUGHT = new DialogueStyle(
            0x80101018, NO_BORDER, 0xFFAAAACC, 0xFFC0C0E0, 0xFFAAAAAA, 0xFFFFFF55,
            TextRevealMode.TYPEWRITER, 3, DialogueWindowAnimation.FADE, 250, DialogueLayout.DEFAULT);

    /** Lower background opacity, muted text — quiet on purpose. */
    public static final DialogueStyle WHISPER = new DialogueStyle(
            0x60000000, NO_BORDER, 0xFFAAAAAA, 0xFF999999, 0xFFAAAAAA, 0xFFFFFF55,
            TextRevealMode.TYPEWRITER, 2, DialogueWindowAnimation.FADE, 250, DialogueLayout.DEFAULT);

    /** Slightly more opaque, high-contrast warm text, faster reveal. Appears instantly (no fade) — a shout shouldn't ease in. */
    public static final DialogueStyle SHOUT = new DialogueStyle(
            0x90200000, NO_BORDER, 0xFFFFCC00, 0xFFFFEEEE, 0xFFAAAAAA, 0xFFFFFF55,
            TextRevealMode.TYPEWRITER, 1, DialogueWindowAnimation.NONE, 0, DialogueLayout.DEFAULT);

    /** Dark, desaturated — speech through gritted teeth. */
    public static final DialogueStyle MUTTER = new DialogueStyle(
            0x80181414, NO_BORDER, 0xFF998888, 0xFFAA9999, 0xFFAAAAAA, 0xFFFFFF55,
            TextRevealMode.TYPEWRITER, 2, DialogueWindowAnimation.FADE, 250, DialogueLayout.DEFAULT);

    public static DialogueStyle forMode(DialogueMode mode) {
        return switch (mode) {
            case NORMAL -> DEFAULT;
            case THOUGHT -> THOUGHT;
            case WHISPER -> WHISPER;
            case SHOUT -> SHOUT;
            case MUTTER -> MUTTER;
        };
    }
}
