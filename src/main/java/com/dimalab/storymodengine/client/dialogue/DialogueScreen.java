package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.dialogue.DialogueChoice;
import com.dimalab.storymodengine.common.dialogue.network.DialogueChoicePacket;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Opened only when a genuine decision is needed — a real modal {@link Screen}, unlike {@link
 * DialogueWindow} (a non-modal overlay for plain lines). Picking an option needs focused mouse/
 * keyboard capture the way an overlay deliberately doesn't have; {@code
 * ClientDialoguePlayer.onStep} opens this in place of the overlay the instant a step points at a
 * {@code DialogueChoiceGroup}, and closes it (returning to overlay-driven lines, or ending the
 * dialogue) the instant the next step arrives.
 */
public final class DialogueScreen extends Screen {

    private final ResourceLocation dialogueId;
    private final DialogueStyle style;
    private final List<DialogueChoice> choices;
    private int selectedIndex;

    public DialogueScreen(ResourceLocation dialogueId, DialogueStyle style, List<DialogueChoice> choices) {
        super(Component.literal("Dialogue Choice"));
        this.dialogueId = dialogueId;
        this.style = style;
        this.choices = choices;
    }

    boolean matches(ResourceLocation otherDialogueId) {
        return dialogueId.equals(otherDialogueId);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int boxWidth = Math.min(420, this.width - 40);
        int x = (this.width - boxWidth) / 2;
        List<String> texts = choices.stream().map(DialogueChoice::text).toList();
        int boxHeight = DialogueWindowRenderer.choicesHeight(font, texts.size());
        int y = (this.height - boxHeight) / 2;
        DialogueWindowRenderer.renderChoices(graphics, font, style, x, y, boxWidth, texts, selectedIndex);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int boxWidth = Math.min(420, this.width - 40);
            int x = (this.width - boxWidth) / 2;
            int boxHeight = DialogueWindowRenderer.choicesHeight(font, choices.size());
            int y = (this.height - boxHeight) / 2;
            int rowHeight = font.lineHeight + 6;
            if (mouseX >= x && mouseX <= x + boxWidth && mouseY >= y + 4 && mouseY <= y + boxHeight - 4) {
                int index = (int) ((mouseY - (y + 4)) / rowHeight);
                if (index >= 0 && index < choices.size()) {
                    select(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 265 /* up */) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            return true;
        }
        if (keyCode == 264 /* down */) {
            selectedIndex = Math.min(choices.size() - 1, selectedIndex + 1);
            return true;
        }
        if (keyCode == 257 || keyCode == 335 /* Enter / Numpad Enter */) {
            select(selectedIndex);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(int index) {
        Network.sendToServer(new DialogueChoicePacket(dialogueId, choices.get(index).id()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
