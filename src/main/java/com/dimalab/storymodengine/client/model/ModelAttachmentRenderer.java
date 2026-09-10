package com.dimalab.storymodengine.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * Draws a generic {@code ModelAttachment} on any {@link LivingEntity} that isn't already served by
 * this engine's own dedicated renderer (see {@code mixin.client.LivingEntityRendererMixin}, the one
 * caller). Owns its own {@link ModelRenderer}/{@link ModelInstanceStore} — a mirror of what {@link
 * GltfModelLayer} owns for {@code NpcRenderer} — since this path has no {@code RenderLayer}/{@code
 * addLayer} framework of its own to hang that state off of.
 *
 * <p>The pose source deliberately reuses the exact same fallback branch {@code NpcRenderer.poseNpc}
 * falls to once a model's own animation controller (if any) is done driving it — see that method's
 * own doc. There is no NPC-specific clip-override step here (nothing to override — a vanilla mob has
 * no {@code npc_play_once}-style manual animation command), so this is simpler than {@code poseNpc},
 * not a divergent copy of it.
 *
 * <p><b>Rotation setup this method must do that {@link GltfModelLayer#draw} does not.</b> {@code
 * GltfModelLayer.draw}'s own {@code translate(0, +1.501, 0)}/{@code scale(-1,-1,1)} pair only
 * <em>undoes</em> {@code LivingEntityRenderer.render()}'s own flip/offset — it assumes that flip and
 * {@code setupRotations}' own body-yaw rotation are already on the {@code PoseStack} by the time it
 * runs, true for {@code NpcRenderer} (a real {@code RenderLayer}, called from inside vanilla's own
 * {@code render()} body, after all of that already ran) but not here: {@code
 * LivingEntityRendererMixin} injects at {@code @At("HEAD")} and cancels immediately, so none of
 * vanilla's own setup ever runs. First symptom of skipping this: the model rendered upside down (the
 * un-applied flip being "undone" against a state that was never flipped in the first place is itself
 * a flip). Fixed by applying the same sequence {@code LivingEntityRenderer.render()} itself does —
 * {@code setupRotations}' own common-case rotation (verified against the decompiled source, the exact
 * formula {@code NpcRenderer.shouldRender}'s own doc already cites for the identical reason), then the
 * flip, then the offset — so {@code GltfModelLayer.draw}'s own undo cancels the flip/offset this
 * method just applied, netting out to just the rotation, matching vanilla's own post-layer-loop state
 * exactly. Like {@code NpcRenderer.shouldRender}, this deliberately skips {@code setupRotations}'
 * dying/sleeping/spin-attack/upside-down branches — an attached-model mob using one of those poses
 * looks slightly off for as long as it's in that pose, not broken.
 */
public final class ModelAttachmentRenderer {

    private static final ModelRenderer MODEL_RENDERER = new ModelRenderer();
    private static final ModelInstanceStore INSTANCES = new ModelInstanceStore();
    // Reused instead of Axis.YP.rotationDegrees(...) allocating a fresh Quaternionf per entity per frame.
    private static final Quaternionf bodyYawRotationScratch = new Quaternionf();

    private ModelAttachmentRenderer() {
    }

    public static void draw(LivingEntity entity, String modelName, PoseStack poseStack, MultiBufferSource bufferSource,
                             int packedLight, float limbSwing, float limbSwingAmount, float partialTick,
                             float ageInTicks, float bodyYaw, float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        poseStack.mulPose(bodyYawRotationScratch.identity().rotateY((float) Math.toRadians(180.0F - bodyYaw)));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        GltfModelLayer.draw(MODEL_RENDERER, INSTANCES, Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer(),
                entity, modelName, "", ModelAttachmentRenderer::pose,
                poseStack, bufferSource, packedLight, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        poseStack.popPose();
    }

    private static void pose(ModelInstance instance, LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch, float partialTick) {
        if (instance.definition().metadata().hasAnimationController()) {
            return;
        }
        HumanoidPoser.pose(instance, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, entity.getAttackAnim(partialTick));
    }
}
