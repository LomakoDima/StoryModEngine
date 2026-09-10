package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.common.entity.custom.ModelEntity;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;


/**
 * Draws a {@link ModelEntity}'s glTF model. One {@link ModelInstance} per entity, held by {@link
 * ModelInstanceStore} — so each animates on its own clock, the instance is released when the entity
 * is, and a reloaded model rebuilds it.
 *
 * <p>The model is drawn centered horizontally and resting on the entity's feet, matching how {@link
 * com.dimalab.storymodengine.common.model.physics.ModelBounds} builds the hitbox — so what you see
 * and what you collide with occupy the same space.
 */
public class ModelEntityRenderer extends EntityRenderer<ModelEntity> {

    private final ModelRenderer renderer = new ModelRenderer();
    private final ModelInstanceStore instances = new ModelInstanceStore();
    // Reused instead of `new Quaternionf()`/`new Vector3f()` per entity per frame.
    private final Quaternionf yawRotationScratch = new Quaternionf();
    private final Vector3f centerScratch = new Vector3f();

    public ModelEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ModelEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        String name = entity.modelName();
        if (name.isEmpty()) {
            return;
        }
        // Re-resolved every frame (a cached lookup), so a reloaded model reaches props that already
        // exist — the store rebuilds the instance whenever the definition changes.
        ModelInstance instance = instances.instanceFor(entity, ModelPhysics.resolve(name));
        if (instance == null) {
            return;
        }
        // entity.tickCount + partialTick — the same ageInTicks quantity LivingEntityRenderer's own
        // getBob computes for RenderLayer, just built by hand here since plain EntityRenderer doesn't
        // hand it down as a parameter. See ModelInstanceStore#deltaSeconds for why this matters.
        instance.advance(instances.deltaSeconds(entity, entity.tickCount + partialTick));

        var bounds = ModelPhysics.bounds(name);
        Vector3f center = bounds.center(centerScratch);

        poseStack.pushPose();
        poseStack.mulPose(yawRotationScratch.identity().rotateY((float) Math.toRadians(-Mth.wrapDegrees(entityYaw))));
        // Model space -> entity space: drop to the feet and centre horizontally, the same fit the
        // hitbox uses, so the drawn model and its collision box line up.
        poseStack.translate(-center.x, -bounds.min().y, -center.z);
        renderer.render(instance, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY, "", java.util.Map.of());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** Required by {@link EntityRenderer}; this renderer binds per-material textures itself, so the entity-wide texture is never used. */
    @Override
    public ResourceLocation getTextureLocation(ModelEntity entity) {
        return new ResourceLocation("storymodengine", "model/white");
    }
}
