package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.state.TitleCardState;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCard;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCardContext;
import com.dimalab.storymodengine.common.cinematic.titlecard.TitleCardRuntime;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The single client-side driver for whichever title card is currently showing — mirrors {@code
 * ClientCutscenePlayer}, much smaller: one {@code onClientTick}, no camera/actor/render-frame
 * concerns at all (a title card is a flat GUI overlay, not a 3D scene). Runs entirely from the
 * {@link TitleCard} the one {@code PlayTitleCardPacket} carried — nothing more is received from
 * the server for the rest of playback.
 *
 * <p>Deliberately independent of {@code ClientCutscenePlayer}/{@code FadeOverlay}: routing a title
 * card's black background through the cutscene system's shared {@code FadeOverlay} static field
 * would let a concurrently-active cutscene fade and a title card fight over the same mutable state
 * every tick. {@code TitleCardOverlay} draws its own background instead.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class ClientTitleCardPlayer {

    private static TitleCardRuntime runtime;

    private ClientTitleCardPlayer() {
    }

    public static void play(TitleCard titleCard) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            EngineLog.channel("Cinematic").warn("Cannot play a title card — no local player yet");
            return;
        }
        if (runtime != null && !runtime.isComplete()) {
            stopInternal();
        }
        runtime = new TitleCardRuntime(titleCard, new TitleCardContext(minecraft.player));
        runtime.play();
    }

    public static void stop() {
        if (runtime != null) {
            stopInternal();
        }
    }

    private static void stopInternal() {
        runtime.cancel();
        TitleCardOverlay.clear();
        runtime = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || runtime == null || runtime.isComplete()) {
            return;
        }
        try {
            int tick = runtime.instance().currentTick();
            TitleCardState state = runtime.instance().definition().evaluate(tick, 0f);
            TitleCardOverlay.show(state);

            runtime.tickOnce();
            if (runtime.isComplete()) {
                TitleCardOverlay.clear();
                runtime = null;
            }
        } catch (Exception e) {
            EngineLog.channel("Cinematic").error("Title card playback threw during client tick — stopping it", e);
            stopInternal();
        }
    }
}
