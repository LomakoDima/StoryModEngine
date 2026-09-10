package com.dimalab.storymodengine.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;

/**
 * An {@link EntityModel} that draws nothing.
 *
 * <p>It exists so a glTF-rendered mob can still be a {@code LivingEntityRenderer}. That renderer is
 * parameterised on a vanilla {@code EntityModel} — the very thing glTF replaces — but it is also
 * where all of a living entity's <b>behaviour</b> lives: the red flash when hurt, toppling over on
 * death, the shake when freezing, invisibility and glow handling, the interpolated head yaw/pitch
 * handed to layers, plus the name tag, shadow, fire and leash that {@code EntityRenderer} adds.
 * Rendering from an {@code EntityRenderer} instead, as the first version did, silently gave all of
 * that up — which is exactly why the mob never flashed red.
 *
 * <p>So the body is a no-op and the geometry is drawn by {@link GltfModelLayer} instead. A layer runs
 * inside the same posed {@code PoseStack} vanilla has already set up, so the model inherits every one
 * of those behaviours rather than reimplementing them.
 */
public class GltfEntityModel<T extends Entity> extends EntityModel<T> {

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Posing happens on the glTF instance, in GltfModelLayer.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int overlay, float red, float green, float blue, float alpha) {
        // Nothing: the layer draws the real geometry.
    }
}
