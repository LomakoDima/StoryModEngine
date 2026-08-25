package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.dialogue.DialogueChoice;
import com.dimalab.storymodengine.common.dialogue.DialogueChoiceGroup;
import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.DialogueEntry;
import com.dimalab.storymodengine.common.dialogue.DialogueLine;
import com.dimalab.storymodengine.api.dialogue.DialogueMode;
import com.dimalab.storymodengine.common.dialogue.network.DialogueStepPacket;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The single client-side driver reacting to dialogue packets — decides which of {@link
 * DialogueWindow} (a non-modal overlay, for a plain line) or {@link DialogueScreen} (a modal
 * choice-only {@code Screen}) is showing at any moment, exactly the split the task's own
 * "{@code DialogueSystem → DialogueWindow → line}" / "{@code DialogueSystem → DialogueScreen →
 * Choice}" diagram describes — never both at once.
 */
public final class ClientDialoguePlayer {

    private ClientDialoguePlayer() {
    }

    public static void onStep(DialogueStepPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            EngineLog.channel("Dialogue").warn("Received a dialogue step with no local player yet");
            return;
        }

        DialogueDefinition definition = DialogueRegistry.get(packet.dialogueId());
        DialogueEntry entry = resolveEntry(definition, packet.nodeId(), packet.entryIndex());
        DialogueStyle style = DialogueStyle.forMode(entry instanceof DialogueLine line ? line.mode() : DialogueMode.NORMAL);

        if (entry instanceof DialogueLine line) {
            closeChoiceScreenIfOpen(minecraft, packet.dialogueId());
            DialogueWindow.show(packet.dialogueId(), line, style);
        } else if (entry instanceof DialogueChoiceGroup group) {
            DialogueWindow.hide();
            List<DialogueChoice> visible = new ArrayList<>();
            for (DialogueChoice choice : group.choices()) {
                if (packet.availableChoiceIds().contains(choice.id())) {
                    visible.add(choice);
                }
            }
            minecraft.setScreen(new DialogueScreen(packet.dialogueId(), style, visible));
        } else {
            EngineLog.channel("Dialogue").warn(
                    "Dialogue step for {} points at an unresolvable entry (node={}, index={}) — nothing to show",
                    packet.dialogueId(), packet.nodeId(), packet.entryIndex());
        }
    }

    public static void onStop() {
        Minecraft minecraft = Minecraft.getInstance();
        DialogueWindow.hide();
        if (minecraft.screen instanceof DialogueScreen) {
            minecraft.setScreen(null);
        }
    }

    private static DialogueEntry resolveEntry(DialogueDefinition definition, String nodeId, int entryIndex) {
        if (definition == null || definition.node(nodeId) == null) {
            return null;
        }
        List<DialogueEntry> entries = definition.node(nodeId).entries();
        return entryIndex >= 0 && entryIndex < entries.size() ? entries.get(entryIndex) : null;
    }

    private static void closeChoiceScreenIfOpen(Minecraft minecraft, ResourceLocation dialogueId) {
        Screen screen = minecraft.screen;
        if (screen instanceof DialogueScreen dialogueScreen && dialogueScreen.matches(dialogueId)) {
            minecraft.setScreen(null);
        }
    }
}
