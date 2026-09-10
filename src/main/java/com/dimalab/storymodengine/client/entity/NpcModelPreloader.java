package com.dimalab.storymodengine.client.entity;

import com.dimalab.storymodengine.client.model.LazyModelLoader;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kicks off a model's async load the moment a client sees an NPC wearing it — earlier than that NPC's
 * first {@code shouldRender}/render attempt, which is otherwise the first time {@code
 * LazyModelLoader} would learn about it (via {@code GltfModelLayer#instanceFor} /
 * {@code NpcRenderer#shouldRender}). Fires once per NPC entering the client's own level (a fresh
 * spawn, or this client re-entering render distance of one), not per frame — {@link
 * LazyModelLoader#requestByName} is itself idempotent, so nothing repeats the work on every
 * subsequent render.
 *
 * <p>Lives here, in {@code client.entity}, rather than on {@code NpcEntity} itself ({@code
 * common.entity.npc}, loaded on both sides) — the same reason {@link LazyModelLoader} lives in {@code
 * client.model} rather than {@code common.model}: this reaches {@code Minecraft.getInstance()}
 * (transitively, through {@code LazyModelLoader}), which {@code common} deliberately stays free of
 * (see {@code ModelRegistry#onReset}'s own doc for the same boundary, solved there with a callback
 * instead). {@code EntityJoinLevelEvent} fires on both logical sides — even within one singleplayer
 * JVM, where the integrated server and the client share a process — so the {@code isClientSide()}
 * check below is still required despite this whole class only ever being <i>loaded</i> on {@code
 * Dist.CLIENT}.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class NpcModelPreloader {

    private NpcModelPreloader() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof NpcEntity npc)) {
            return;
        }
        String modelName = npc.modelName();
        if (!modelName.isEmpty()) {
            LazyModelLoader.requestByName(modelName);
        }
    }
}
