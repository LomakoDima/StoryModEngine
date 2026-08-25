package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.dialogue.DialogueLine;
import com.dimalab.storymodengine.common.dialogue.DialogueWindowAnimation;
import com.dimalab.storymodengine.api.dialogue.TextRevealMode;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/**
 * The current line/style/animation-timing state for the {@link DialogueWindow} overlay — plain
 * data plus small derived queries, no rendering and no {@code GuiGraphics} reference anywhere in
 * this class (that's {@link DialogueWindowRenderer}'s job entirely). Mirrors {@code
 * math.example.EasingDemoScreen}'s own technique for frame-time animation: timestamps sampled via
 * {@link Util#getMillis()}, never a game-tick counter, so progress is smooth regardless of tick
 * rate/lag.
 *
 * <p>{@link #hide()} does not clear the line immediately — it starts an exit timer, and {@link
 * #isHideComplete} tells the caller ({@link DialogueWindow}) once it's safe to actually forget the
 * line, so an exit animation has time to actually play before rendering stops.
 */
final class DialogueWindowState {

    private ResourceLocation dialogueId;
    private DialogueLine line;
    private DialogueStyle style;
    private final TypewriterState typewriter = new TypewriterState();

    private long shownAtMillis;
    private boolean hiding;
    private long hideStartedAtMillis;

    void show(ResourceLocation dialogueId, DialogueLine line, DialogueStyle style) {
        this.dialogueId = dialogueId;
        this.line = line;
        this.style = style;
        this.shownAtMillis = Util.getMillis();
        this.hiding = false;

        TextRevealMode revealMode = line.revealMode() != null ? line.revealMode() : style.revealMode();
        int charactersPerTick = line.charactersPerTick() != null ? line.charactersPerTick() : style.charactersPerTick();
        typewriter.start(line.text(), charactersPerTick);
        if (revealMode == TextRevealMode.INSTANT) {
            typewriter.revealAll();
        }
    }

    /** Begins the exit animation. A no-op if nothing is showing or an exit is already in progress. */
    void hide() {
        if (line == null || hiding) {
            return;
        }
        hiding = true;
        hideStartedAtMillis = Util.getMillis();
    }

    /** Once true, {@link #reset} is safe to call — the exit animation (if any) has finished playing. */
    boolean isHideComplete(long nowMillis) {
        return hiding && nowMillis - hideStartedAtMillis >= animationDurationMs();
    }

    void reset() {
        line = null;
        hiding = false;
    }

    boolean isVisible() {
        return line != null;
    }

    boolean isHiding() {
        return hiding;
    }

    void tick() {
        if (line != null) {
            typewriter.tick();
        }
    }

    ResourceLocation dialogueId() {
        return dialogueId;
    }

    DialogueLine line() {
        return line;
    }

    DialogueStyle style() {
        return style;
    }

    TypewriterState typewriter() {
        return typewriter;
    }

    DialogueWindowAnimation animation() {
        DialogueWindowAnimation lineAnimation = line != null ? line.animation() : null;
        return lineAnimation != null ? lineAnimation : style.animation();
    }

    int animationDurationMs() {
        return animation() == DialogueWindowAnimation.NONE || style == null ? 0 : style.animationDurationMs();
    }

    /** 0 right as {@link #show} was called, 1 once the entrance animation has fully played. */
    float entranceProgress(long nowMillis) {
        int duration = animationDurationMs();
        return duration <= 0 ? 1f : clamp01((nowMillis - shownAtMillis) / (float) duration);
    }

    /** 0 right as {@link #hide} was called, 1 once the exit animation has fully played. */
    float exitProgress(long nowMillis) {
        int duration = animationDurationMs();
        return !hiding || duration <= 0 ? 0f : clamp01((nowMillis - hideStartedAtMillis) / (float) duration);
    }

    /** Combined 0..1 visibility — entrance ramping up, or exit ramping back down, whichever applies right now. */
    float visibility(long nowMillis) {
        return hiding ? 1f - exitProgress(nowMillis) : entranceProgress(nowMillis);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
