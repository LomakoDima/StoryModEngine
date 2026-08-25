package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.state.Subtitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws whatever {@link Subtitle} {@link ClientCutscenePlayer} last set — the one place this
 * system actually renders subtitle text, kept deliberately separate from {@link Subtitle} itself
 * (plain data) so a future UI system can replace this class entirely without touching {@code
 * SubtitleTrack} or anything else that produces subtitle data. {@code opacity} is passed in fresh
 * every tick (via {@link Subtitle#opacityAt}) rather than recomputed here, so this class stays
 * ignorant of tick numbers entirely.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class SubtitleOverlay {

    private static volatile Subtitle current;
    private static volatile float opacity = 1f;

    private SubtitleOverlay() {
    }

    static void show(Subtitle subtitle, float opacity) {
        current = subtitle;
        SubtitleOverlay.opacity = opacity;
    }

    static void clear() {
        current = null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Subtitle subtitle = current;
        if (subtitle == null || opacity <= 0f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        String text = subtitle.speaker() != null ? subtitle.speaker() + ": " + subtitle.text() : subtitle.text();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int textWidth = minecraft.font.width(text);
        int y = subtitle.position() == Subtitle.Position.TOP ? 20 : screenHeight - 40;
        int alpha = Mth.clamp(Math.round(Mth.clamp(opacity, 0f, 1f) * 255f), 0, 255);
        int argb = (alpha << 24) | (subtitle.rgbColor() & 0xFFFFFF);
        graphics.drawString(minecraft.font, text, (screenWidth - textWidth) / 2, y, argb);
    }
}
