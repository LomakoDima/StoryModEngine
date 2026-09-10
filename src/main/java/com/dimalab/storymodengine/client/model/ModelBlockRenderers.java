package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.model.block.ModelBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Binds {@link ModelBlockRenderer} to the model block's entity type. {@code
 * EntityRenderersEvent.RegisterRenderers} is where block entity renderers register too, despite the
 * name — same event, {@code registerBlockEntityRenderer} instead of {@code registerEntityRenderer}.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModelBlockRenderers {

    private ModelBlockRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModelBlocks.MODEL_BLOCK_ENTITY.get(), ModelBlockRenderer::new);
    }
}
