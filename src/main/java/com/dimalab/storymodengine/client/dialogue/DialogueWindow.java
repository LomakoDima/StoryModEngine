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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

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
 *
 * <p>Also suppresses whichever vanilla HUD overlays would otherwise compete with the box for screen
 * space ({@link #onRenderOverlay}) — the exact same blocklist-by-default technique {@code
 * TitleCardOverlay} uses (cancel everything unless it's on a short keep-list, so a future overlay
 * neither of us has heard of is suppressed by default too, instead of chasing bug reports one
 * overlay at a time). The keep-list is different from the title card's, though, and deliberately
 * so: a title card is a full-screen modal moment where nothing but the text should show, but this
 * window is non-modal — the player keeps fighting/moving/mining underneath it (see above) and still
 * needs the HUD that supports that: hotbar, crosshair, health/hunger/armor/air, the vehicle/mount
 * and experience bars, and the effect icons. What actually gets hidden is the handful of overlays
 * that are themselves blocks of text competing for the same screen real estate as a dialogue line —
 * chat foremost (the literal complaint this was built for), plus subtitles and vanilla's own
 * title/subtitle text, which default to the same bottom-/center-screen territory this box does.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class DialogueWindow {

    private static final DialogueWindowState STATE = new DialogueWindowState();
    private static boolean continueWasDown;

    /** Built lazily — see {@code TitleCardOverlay#keepVisible} for why this can't be a {@code static final} field initializer. */
    private static Set<NamedGuiOverlay> keepVisible;

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

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (STATE.isVisible() && !keepVisible().contains(event.getOverlay())) {
            event.setCanceled(true);
        }
    }

    private static Set<NamedGuiOverlay> keepVisible() {
        Set<NamedGuiOverlay> set = keepVisible;
        if (set == null) {
            set = Set.of(
                    VanillaGuiOverlay.HOTBAR.type(),
                    VanillaGuiOverlay.CROSSHAIR.type(),
                    VanillaGuiOverlay.PLAYER_HEALTH.type(),
                    VanillaGuiOverlay.ARMOR_LEVEL.type(),
                    VanillaGuiOverlay.FOOD_LEVEL.type(),
                    VanillaGuiOverlay.AIR_LEVEL.type(),
                    VanillaGuiOverlay.MOUNT_HEALTH.type(),
                    VanillaGuiOverlay.JUMP_BAR.type(),
                    VanillaGuiOverlay.EXPERIENCE_BAR.type(),
                    VanillaGuiOverlay.POTION_ICONS.type(),
                    VanillaGuiOverlay.ITEM_NAME.type(),
                    VanillaGuiOverlay.VIGNETTE.type(),
                    VanillaGuiOverlay.SPYGLASS.type(),
                    VanillaGuiOverlay.HELMET.type(),
                    VanillaGuiOverlay.FROSTBITE.type(),
                    VanillaGuiOverlay.PORTAL.type(),
                    VanillaGuiOverlay.SLEEP_FADE.type(),
                    VanillaGuiOverlay.SCOREBOARD.type(),
                    VanillaGuiOverlay.PLAYER_LIST.type(),
                    VanillaGuiOverlay.DEBUG_TEXT.type(),
                    VanillaGuiOverlay.FPS_GRAPH.type(),
                    VanillaGuiOverlay.RECORD_OVERLAY.type());
            keepVisible = set;
        }
        return set;
    }
}
