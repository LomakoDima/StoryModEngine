package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.api.dialogue.DialogueMode;
import com.dimalab.storymodengine.api.dialogue.TextRevealMode;
/**
 * One line of dialogue text. {@code portrait}/{@code emotion}/{@code voiceId}/{@code revealMode}/
 * {@code charactersPerTick}/{@code animation} are all nullable — {@code null} means "use the
 * dialogue's own {@link com.dimalab.storymodengine.client.dialogue.DialogueStyle} default," never a
 * sentinel value the renderer has to special-case beyond a null check. {@code portrait} overrides
 * whatever {@link DialogueSpeakerRegistry#get(String)} would resolve for {@code speaker} — set it
 * per-line only when a specific line needs a different portrait than that speaker's usual one; leave
 * it {@code null} to just use the registered speaker's (or show none, if that's unregistered too).
 * {@code durationTicks} is stored but not acted on by {@link DialogueRunner}'s compiler in this
 * version — every line waits for an explicit Continue; a future auto-advance mode can read it
 * without a shape change here. {@code mode} (see {@link DialogueMode}) and {@code animation} (see
 * {@link DialogueWindowAnimation}) are pure presentation hints resolved client-side — the compiler
 * never branches on either.
 *
 * <p>{@link #builder(String, String)} is the ergonomic path for anything beyond the plain
 * speaker+text case; {@link DialogueDefinition.Builder#line(DialogueLine)} accepts the result
 * directly alongside the shorthand {@code #line(String, String)} overload.
 *
 * <p>{@code nameColor} overrides whatever {@link DialogueSpeakerRegistry#get(String)} would resolve
 * for {@code speaker} (or the active {@code DialogueStyle}'s default, if that speaker isn't
 * registered either) — the same per-line-override shape as {@code portrait}, for a line that needs
 * a one-off name color without registering a whole {@link DialogueSpeaker}.
 */
public record DialogueLine(
        String speaker,
        String text,
        String portrait,
        String emotion,
        String voiceId,
        int durationTicks,
        TextRevealMode revealMode,
        Integer charactersPerTick,
        DialogueMode mode,
        DialogueWindowAnimation animation,
        Integer nameColor
) implements DialogueEntry {

    public DialogueLine(String speaker, String text) {
        this(speaker, text, null, null, null, 0, null, null, DialogueMode.NORMAL, null, null);
    }

    public static Builder builder(String speaker, String text) {
        return new Builder(speaker, text);
    }

    public static final class Builder {
        private final String speaker;
        private final String text;
        private String portrait;
        private String emotion;
        private String voiceId;
        private int durationTicks;
        private TextRevealMode revealMode;
        private Integer charactersPerTick;
        private DialogueMode mode = DialogueMode.NORMAL;
        private DialogueWindowAnimation animation;
        private Integer nameColor;

        private Builder(String speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }

        public Builder portrait(String portrait) {
            this.portrait = portrait;
            return this;
        }

        public Builder emotion(String emotion) {
            this.emotion = emotion;
            return this;
        }

        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        public Builder duration(int durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        public Builder revealMode(TextRevealMode revealMode) {
            this.revealMode = revealMode;
            return this;
        }

        public Builder charactersPerTick(int charactersPerTick) {
            this.charactersPerTick = charactersPerTick;
            return this;
        }

        public Builder mode(DialogueMode mode) {
            this.mode = mode;
            return this;
        }

        /** Overrides the active style's default entrance/exit animation for just this line. */
        public Builder animation(DialogueWindowAnimation animation) {
            this.animation = animation;
            return this;
        }

        /** Overrides the registered {@link DialogueSpeaker}'s (or style default's) name color for just this line. */
        public Builder nameColor(int nameColor) {
            this.nameColor = nameColor;
            return this;
        }

        public DialogueLine build() {
            return new DialogueLine(speaker, text, portrait, emotion, voiceId, durationTicks, revealMode, charactersPerTick, mode, animation, nameColor);
        }
    }
}
