package com.dimalab.storymodengine.common.dialogue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A plain {@code id -> DialogueSpeaker} map — no annotation discovery of its own (too small a piece
 * to justify one; a mod author calls {@link #register} directly, typically once per speaker, next
 * to wherever their dialogues are defined). Purely presentational data, read only by client-side
 * rendering ({@code dialogue.client.DialoguePresenter}) — never consulted by {@link DialogueRunner}
 * or anything server-authoritative.
 */
public final class DialogueSpeakerRegistry {

    private static final Map<String, DialogueSpeaker> SPEAKERS = new ConcurrentHashMap<>();

    private DialogueSpeakerRegistry() {
    }

    public static void register(DialogueSpeaker speaker) {
        SPEAKERS.put(speaker.id(), speaker);
    }

    /** The registered metadata for {@code speakerId}, or {@code null} if none was registered — a normal, expected case. */
    public static DialogueSpeaker get(String speakerId) {
        return SPEAKERS.get(speakerId);
    }
}
