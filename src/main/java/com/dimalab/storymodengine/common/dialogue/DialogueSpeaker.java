package com.dimalab.storymodengine.common.dialogue;

import net.minecraft.resources.ResourceLocation;

/**
 * Reusable presentation metadata for a speaker id ({@link DialogueLine#speaker()} stays a plain
 * {@code String}, unchanged) — a display name, an optional portrait texture, and a name color, so
 * the same "Guard" id used across several dialogues doesn't repeat this everywhere it's spoken.
 * Registering one is entirely optional: {@link DialogueSpeakerRegistry#get} resolving to nothing is
 * a normal, fully-supported case — the raw speaker id is shown instead, same as before this existed.
 */
public record DialogueSpeaker(String id, String displayName, ResourceLocation portrait, int nameColor) {

    public DialogueSpeaker(String id, String displayName, int nameColor) {
        this(id, displayName, null, nameColor);
    }
}
