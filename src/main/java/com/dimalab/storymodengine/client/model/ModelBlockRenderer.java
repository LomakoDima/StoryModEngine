package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.block.ModelBlock;
import com.dimalab.storymodengine.common.model.block.ModelBlockEntity;
import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.dimalab.storymodengine.common.model.physics.ModelRotation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a placed {@link ModelBlockEntity}'s model. The block itself renders nothing (its {@code
 * RenderShape} is {@code ENTITYBLOCK_ANIMATED}), so this is the only thing that puts pixels there.
 *
 * <p>Fits the model into its block the same way {@code ModelVoxelizer.forBlock} fits the collision
 * shape — uniform scale to the largest axis, centred horizontally, resting on the block floor — so
 * the drawn mesh and the shape you collide with occupy exactly the same space. Getting these two
 * fits out of step would be invisible in code review and obvious in game as collision that doesn't
 * match what's drawn.
 */
public class ModelBlockRenderer implements BlockEntityRenderer<ModelBlockEntity> {

    private final ModelRenderer renderer = new ModelRenderer();
    private final Map<String, ModelInstance> instances = new HashMap<>();
    // Reused instead of `new Quaternionf()`/`new Vector3f()` per block entity per frame.
    private final Quaternionf yawRotationScratch = new Quaternionf();
    private final Vector3f centerScratch = new Vector3f();

    public ModelBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ModelBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String name = blockEntity.modelName();
        if (name.isEmpty()) {
            return;
        }
        ModelInstance instance = instances.computeIfAbsent(name, key -> {
            ModelDefinition definition = ModelPhysics.resolve(key);
            return definition == null ? null : new ModelInstance(definition);
        });
        if (instance == null) {
            return;
        }

        ModelBounds bounds = ModelPhysics.bounds(name);
        if (bounds.isEmpty()) {
            return;
        }
        float largest = Math.max(bounds.sizeX(), Math.max(bounds.sizeY(), bounds.sizeZ()));
        if (largest <= 0f) {
            return;
        }
        float scale = 1f / largest;
        Vector3f center = bounds.center(centerScratch);
        Direction facing = blockEntity.getBlockState().getValue(ModelBlock.FACING);

        poseStack.pushPose();
        // Rotate about the block's centre, by the angle that matches the collision shape's own
        // quarter-turn rotation — see ModelBlock.renderYawFor for why this isn't facing.toYRot().
        poseStack.translate(0.5f, 0f, 0.5f);
        poseStack.mulPose(yawRotationScratch.identity().rotateY((float) Math.toRadians(ModelRotation.renderYawFor(facing))));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-center.x, -bounds.min().y, -center.z);

        renderer.render(instance, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, "", java.util.Map.of());
        poseStack.popPose();
    }
}
