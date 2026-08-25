package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.dialogue.DialogueLine;
import com.dimalab.storymodengine.common.dialogue.network.DialogueChoicePacket;
import com.dimalab.storymodengine.common.network.Network;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A non-modal HUD overlay for whichever {@link DialogueLine} is currently showing — mirrors {@code
 * cinematic.client.FadeOverlay} in spirit (a static control class driving one piece of state, one
 * {@code RenderGuiEvent.Post} draw), but delegates *what* is showing to {@link DialogueWindowState}
 * and *how it looks* to {@link DialogueWindowRenderer} rather than holding either itself — this
 * class is now just the glue: one state instance, {@link #show}/{@link #hide} entry points, and the
 * two {@code @SubscribeEvent} hooks that drive it every tick/frame.
 *
 * <p>The player keeps full control of the camera/movement while a line is showing — a real choice
 * opens {@link DialogueScreen} instead, which *does* capture input; this class only ever handles
 * plain lines/thoughts/whispers/shouts. Advancing needs a dedicated {@link
 * DialogueContinueKeyMapping} rather than a mouse click, specifically because this is non-modal: a
 * click here would still reach the world underneath (swing the held item, break/place a block).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class DialogueWindow {

    private static final DialogueWindowState STATE = new DialogueWindowState();
    private static boolean continueWasDown;

    private DialogueWindow() {
    }

    static void show(ResourceLocation dialogueId, DialogueLine line, DialogueStyle style) {
        STATE.show(dialogueId, line, style);
    }

    /** Begins the exit animation rather than clearing immediately — see {@link DialogueWindowState#hide}. */
    static void hide() {
        STATE.hide();
    }

    static boolean isShowing() {
        return STATE.isVisible();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (STATE.isHideComplete(Util.getMillis())) {
            STATE.reset();
        }
        if (!STATE.isVisible()) {
            continueWasDown = false;
            return;
        }
        STATE.tick();

        boolean down = DialogueContinueKeyMapping.CONTINUE.isDown();
        if (down && !continueWasDown && !STATE.isHiding()) {
            onContinuePressed();
        }
        continueWasDown = down;
    }

    private static void onContinuePressed() {
        if (!STATE.typewriter().isComplete()) {
            STATE.typewriter().revealAll();
            return;
        }
        Network.sendToServer(new DialogueChoicePacket(STATE.dialogueId(), "continue"));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!STATE.isVisible()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        DialogueWindowRenderer.render(graphics, minecraft.font, STATE, screenWidth, screenHeight, Util.getMillis());
    }
}
