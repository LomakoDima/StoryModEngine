package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.state.TitleCardState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Draws whatever {@link TitleCardState} {@link ClientTitleCardPlayer} last set — the one place
 * this system actually renders anything, mirroring {@code SubtitleOverlay}/{@code FadeOverlay}. A
 * genuine client-side GUI overlay ({@code RenderGuiEvent.Post} + {@code GuiGraphics}), not a world
 * hack (no entities, particles, bossbars, or signs). Draws its own full-screen black rectangle
 * rather than routing through {@code FadeOverlay} — see {@link ClientTitleCardPlayer}'s Javadoc for
 * why sharing that static field would be unsafe here.
 *
 * <p>Also cancels every vanilla HUD overlay ({@link #onRenderOverlay}) while a card is showing,
 * except {@link #KEEP_VISIBLE}. First built as a chat-only fix, then a second live report showed
 * the hotbar leaking through the same way — rather than chase individual overlays one bug report at
 * a time (crosshair, health, experience, potion icons, ... are all the same class of problem), this
 * is a blocklist-by-default: everything gets suppressed unless explicitly kept, so a future vanilla/
 * Forge overlay this class has never heard of is suppressed by default too, matching the task's own
 * "full-screen black screen" requirement instead of silently leaking through. {@code DEBUG_TEXT}/
 * {@code FPS_GRAPH}/{@code RECORD_OVERLAY} are kept — a developer who explicitly opened the F3
 * debug screen almost certainly still wants it while previewing a title card.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class TitleCardOverlay {

    private static final float TITLE_SCALE = 2f;
    private static final float SUBTITLE_SCALE = 1f;

    /**
     * Built lazily, on first use, never as a {@code static final} field initializer:
     * {@code VanillaGuiOverlay#type()} is {@code null} until {@code GuiOverlayManager} populates it
     * right after {@code RegisterGuiOverlaysEvent} finishes firing (verified against
     * {@code GuiOverlayManager} source, whose own Javadoc says as much) — which is well after mod
     * construction, when a {@code static final} field here would already have run and crashed the
     * whole client with a {@code NullPointerException} out of {@code Set.of(...)}. The first
     * {@link #onRenderOverlay} call happens during actual HUD rendering, long after that event, so
     * lazy init here is safe.
     */
    private static Set<NamedGuiOverlay> keepVisible;

    private static volatile TitleCardState current;

    private TitleCardOverlay() {
    }

    static void show(TitleCardState state) {
        current = state;
    }

    static void clear() {
        current = null;
    }

    private static boolean isShowing() {
        TitleCardState state = current;
        return state != null && state.opacity() > 0f;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (isShowing() && !keepVisible().contains(event.getOverlay())) {
            event.setCanceled(true);
        }
    }

    private static Set<NamedGuiOverlay> keepVisible() {
        Set<NamedGuiOverlay> set = keepVisible;
        if (set == null) {
            set = Set.of(
                    VanillaGuiOverlay.DEBUG_TEXT.type(),
                    VanillaGuiOverlay.FPS_GRAPH.type(),
                    VanillaGuiOverlay.RECORD_OVERLAY.type());
            keepVisible = set;
        }
        return set;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        TitleCardState state = current;
        if (state == null || state.opacity() <= 0f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int alpha = Mth.clamp(Math.round(state.opacity() * 255f), 0, 255);

        graphics.fill(0, 0, screenWidth, screenHeight, alpha << 24);

        int textAlpha = (alpha << 24) | 0xFFFFFF;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        drawScaledCentered(graphics, minecraft, state.title(), centerX, centerY - 10, TITLE_SCALE, textAlpha);
        state.subtitle().ifPresent(subtitle ->
                drawScaledCentered(graphics, minecraft, subtitle, centerX, centerY + 14, SUBTITLE_SCALE, textAlpha));
    }

    private static void drawScaledCentered(GuiGraphics graphics, Minecraft minecraft, Component text, int x, int y, float scale, int color) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, scale);
        graphics.drawCenteredString(minecraft.font, text, 0, 0, color);
        pose.popPose();
    }
}
