package com.dimalab.storymodengine.client.model.debug;

import com.dimalab.storymodengine.client.model.ModelInstance;
import com.dimalab.storymodengine.client.model.ModelRenderer;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders the single active {@code /sme model test} anchor, if any — a visual smoke test
 * for the whole import → pose → skin → render pipeline, not a real in-world object (nothing is
 * spawned, nothing persists across a relog, mirroring {@code raycast}'s "debug leaves no world state
 * behind" discipline). One anchor at a time; a new {@code /model test} replaces it.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ModelDebugRenderStage {

    private static final ModelRenderer RENDERER = new ModelRenderer();

    private static volatile ModelInstance activeInstance;
    private static volatile Vec3 activePosition;
    private static long lastFrameNanos;

    private ModelDebugRenderStage() {
    }

    public static void show(ModelInstance instance, Vec3 position) {
        activeInstance = instance;
        activePosition = position;
        lastFrameNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ModelInstance instance = activeInstance;
        Vec3 position = activePosition;
        if (instance == null || position == null) {
            return;
        }

        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.25f);
        lastFrameNanos = now;
        instance.advance(deltaSeconds);

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        RENDERER.render(instance, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY, "", java.util.Map.of());
        bufferSource.endBatch();
        poseStack.popPose();
    }
}
