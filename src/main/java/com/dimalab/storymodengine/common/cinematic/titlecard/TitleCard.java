package com.dimalab.storymodengine.common.cinematic.titlecard;

import com.dimalab.storymodengine.common.cinematic.state.TitleCardState;
import com.dimalab.storymodengine.common.cinematic.track.Track;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, full-screen narrative title card — fade in, hold, fade out, over a title and an
 * optional subtitle. Reusable and player-independent, exactly like {@code CutsceneDefinition}: one
 * {@code TitleCard} can be played by any number of players, any number of times, independently.
 *
 * <pre>{@code
 * TitleCard card = TitleCard.builder(Component.literal("Тем временем..."))
 *         .subtitle(Component.literal("Где-то глубоко под землёй"))
 *         .fadeIn(20).hold(60).fadeOut(20)
 *         .build();
 * }</pre>
 *
 * <p>Unlike {@code CutsceneDefinition} (whose {@code Timeline}/{@code Track}s hold Java lambdas
 * that can never cross the network, which is why that system needs a registry + id), every field
 * here is plain, already-serializable data — so a {@code TitleCard} is sent directly inside {@code
 * PlayTitleCardPacket} rather than through a registry lookup.
 *
 * <p>Implements the same {@link Track}{@code <TitleCardState>} contract every other {@code
 * cinematic} track does — {@link #evaluate} is a pure function of time (no hidden state, same
 * input always produces the same output), reusing the existing {@code math.interp.Interpolators
 * .FLOAT} for the opacity ramp. No {@code Easing} is applied yet (deliberately — see the class's
 * own task notes on keeping v1 simple); attaching one later is a one-line change at the two
 * {@code interpolate(...)} call sites below, not a redesign.
 */
public record TitleCard(Component title, Optional<Component> subtitle, int fadeInTicks, int holdTicks, int fadeOutTicks)
        implements Track<TitleCardState> {

    public TitleCard {
        // Defensive, not developer-facing: this constructor also runs on the network-deserialization
        // path (TitleCard is embedded directly in PlayTitleCardPacket), which must never throw from
        // a malformed value. Builder#build() is what gives a mod author a loud, early failure — see
        // its own validation below.
        Objects.requireNonNull(title, "TitleCard title must not be null");
        subtitle = subtitle == null ? Optional.empty() : subtitle;
        fadeInTicks = Math.max(0, fadeInTicks);
        holdTicks = Math.max(0, holdTicks);
        fadeOutTicks = Math.max(0, fadeOutTicks);
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public int totalTicks() {
        return fadeInTicks + holdTicks + fadeOutTicks;
    }

    @Override
    public TitleCardState evaluate(int tick, float partialTick) {
        float time = tick + partialTick;
        TitleCardPhase phase;
        float opacity;
        if (time < fadeInTicks) {
            phase = TitleCardPhase.FADE_IN;
            opacity = fadeInTicks == 0 ? 1f : Interpolators.FLOAT.interpolate(0f, 1f, time / fadeInTicks);
        } else if (time < fadeInTicks + holdTicks) {
            phase = TitleCardPhase.HOLD;
            opacity = 1f;
        } else if (time < totalTicks()) {
            phase = TitleCardPhase.FADE_OUT;
            float local = time - fadeInTicks - holdTicks;
            opacity = fadeOutTicks == 0 ? 0f : Interpolators.FLOAT.interpolate(1f, 0f, local / fadeOutTicks);
        } else {
            phase = TitleCardPhase.FADE_OUT;
            opacity = 0f;
        }
        return new TitleCardState(phase, opacity, title, subtitle);
    }

    public static final class Builder {

        private static final int DEFAULT_FADE_IN = 20;
        private static final int DEFAULT_HOLD = 60;
        private static final int DEFAULT_FADE_OUT = 20;

        private final Component title;
        private Optional<Component> subtitle = Optional.empty();
        private int fadeInTicks = DEFAULT_FADE_IN;
        private int holdTicks = DEFAULT_HOLD;
        private int fadeOutTicks = DEFAULT_FADE_OUT;

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "TitleCard title must not be null");
        }

        public Builder subtitle(Component subtitle) {
            this.subtitle = Optional.ofNullable(subtitle);
            return this;
        }

        public Builder fadeIn(int ticks) {
            this.fadeInTicks = ticks;
            return this;
        }

        public Builder hold(int ticks) {
            this.holdTicks = ticks;
            return this;
        }

        public Builder fadeOut(int ticks) {
            this.fadeOutTicks = ticks;
            return this;
        }

        public TitleCard build() {
            if (fadeInTicks < 0 || holdTicks < 0 || fadeOutTicks < 0) {
                throw new IllegalStateException("TitleCard '" + title.getString() + "': durations must not be negative");
            }
            if (fadeInTicks + holdTicks + fadeOutTicks <= 0) {
                throw new IllegalStateException("TitleCard '" + title.getString() + "': total duration must be positive");
            }
            return new TitleCard(title, subtitle, fadeInTicks, holdTicks, fadeOutTicks);
        }
    }
}
