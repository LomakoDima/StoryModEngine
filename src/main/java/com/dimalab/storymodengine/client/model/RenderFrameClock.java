package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A monotonically increasing per-client-frame counter — ticks once per real frame, on {@link
 * TickEvent.RenderTickEvent}'s {@code Phase.START} (same hook {@code ClientCutscenePlayer
 * .onRenderTickStart} already uses for "once per frame, before any entity renders").
 *
 * <p>What {@link ModelInstance} stamps its own {@code posedFrame} against to tell whether it has
 * already been advanced/posed this frame — see {@link ModelInstance#advance}/{@link
 * ModelInstance#advanceAndPoseWith}. Deliberately its own tiny class rather than a field on any one
 * renderer: three independent render paths (NPC's {@code GltfModelLayer}, {@code
 * ModelEntityRenderer}, the {@code /model test} debug anchor) each hold their own {@code
 * ModelInstanceStore}, so there is no single natural owner for "what frame is this" among them.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RenderFrameClock {

    private static long frame = 0L;

    private RenderFrameClock() {
    }

    public static long currentFrame() {
        return frame;
    }

    @SubscribeEvent
    public static void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
        }
    }

    /**
     * Test-only: pins the counter to an explicit value without a running client — {@code
     * RenderTickEvent} never fires in {@code ModelSelfTest}'s headless JVM path, so the counter would
     * otherwise sit at its initial value for the whole test run.
     */
    public static void set(long value) {
        frame = value;
    }
}
