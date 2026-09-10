package com.dimalab.storymodengine.client.entity;

import com.dimalab.storymodengine.client.model.ModelEntityRenderer;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Maps this mod's entity types to their renderers. {@code Dist.CLIENT}-gated the same way every other
 * client-only registration in this project is.
 *
 * <p>There is no {@code RegisterLayerDefinitions} handler: layer definitions exist to bake a vanilla
 * {@code EntityModel} out of hand-written {@code CubeListBuilder} calls, and nothing here builds
 * geometry that way — every model is imported through {@code common.model} and needs no baking.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {

    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.MODEL.get(), ModelEntityRenderer::new);
    }
}
