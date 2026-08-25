package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.state.FadeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws a full-screen color rectangle for whatever {@link FadeState} is currently active — mirrors
 * {@code SubtitleOverlay} exactly (the one place this system renders anything for its concept;
 * {@code FadeTrack}/{@code FadeState} themselves stay plain data). Also used directly by {@code
 * ClientCutscenePlayer} for a {@code Shot}'s {@code FADE} transition, which is just a short,
 * programmatic opacity ramp through this same overlay rather than a separate mechanism.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class FadeOverlay {

    private static volatile FadeState current;

    private FadeOverlay() {
    }

    static void show(FadeState state) {
        current = state;
    }

    static void clear() {
        current = null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        FadeState state = current;
        if (state == null || state.opacity() <= 0f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int alpha = Mth.clamp(Math.round(Mth.clamp(state.opacity(), 0f, 1f) * 255f), 0, 255);
        int argb = (alpha << 24) | (state.rgbColor() & 0xFFFFFF);
        graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), argb);
    }
}
