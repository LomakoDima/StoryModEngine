package com.dimalab.storymodengine.mixin.client;

import com.dimalab.storymodengine.client.model.ModelAttachmentRenderer;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.model.attachment.ModelAttachCapabilities;
import com.dimalab.storymodengine.common.model.attachment.ModelAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Generalizes "wear a glTF model" from an NPC-only special case to any {@link LivingEntity} — a
 * vanilla zombie, the real player, anything carrying a non-empty {@code ModelAttachment} (see that
 * class's own doc and {@code ModelAttachCommand}, the {@code /sme model attach} command that sets it).
 *
 * <p>Injects at {@code @At("HEAD")} — deliberately not deeper into the method with a {@code
 * LocalCapture} of {@code limbSwing}/{@code ageInTicks}/{@code netHeadYaw}/{@code headPitch} the way
 * an earlier Mixin this session captured a single, unambiguous local. Confirmed by reading the real
 * decompiled source (Forge 1.20.1-47.4.22, {@code LivingEntityRenderer.render(...)}): those five
 * values are computed from several interleaved locals (some only conditionally declared, e.g. inside
 * the vehicle-riding and sleeping branches), a genuinely fragile multi-local capture to get exactly
 * right from static reading alone — and {@code LocalCapture.CAPTURE_FAILSOFT}'s failure mode isn't a
 * crash, it's silently substituting {@code 0.0f} for whatever it can't bind, which would render every
 * attached model in a technically-valid but wrong frozen T-ish pose with no error anywhere.
 *
 * <p>Instead, this recomputes the same five values itself, directly from {@link LivingEntity}'s own
 * public fields — {@code walkAnimation}, {@code yBodyRot(O)}, {@code yHeadRot(O)}, {@code xRotO},
 * {@code tickCount} — using the exact formulas {@code LivingEntityRenderer.render()}/{@code getBob()}
 * use (verified against the same decompiled source, not guessed). This is a deliberately narrow,
 * verified re-derivation of a few stable public-field formulas for the specific entities that opt
 * into this replacement path — not the general "reimplement a host renderer's pass" this project
 * otherwise avoids (see {@code NpcRenderer.shouldRender}'s own doc for the same kind of narrow,
 * verified exception, reproducing {@code setupRotations}'s rotation for the same reason).
 *
 * <p>Explicitly skips {@link com.dimalab.storymodengine.common.entity.npc.NpcEntity} — {@code
 * NpcRenderer} already fully owns rendering for that type via its own {@code GltfModelLayer}/{@code
 * getRenderType() -&gt; null}; firing this path there too would double-render.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void storymodengine$renderAttachedModel(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                                                      MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (entity.getType() == ModEntities.NPC.get()) {
            return;
        }
        ModelAttachment attachment = Capabilities.get(entity, ModelAttachCapabilities.MODEL_ATTACHMENT);
        if (attachment == null || attachment.modelName.isEmpty()) {
            return;
        }
        ci.cancel();
        if (entity.isInvisible()) {
            return;
        }

        // Mirrors LivingEntityRenderer.render()'s own "shouldSit" gate — a seated passenger's limbs
        // don't swing, per vanilla's own convention (verified against the same decompiled source).
        boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null && entity.getVehicle().shouldRiderSit();
        float limbSwing = 0.0F;
        float limbSwingAmount = 0.0F;
        if (!shouldSit && entity.isAlive()) {
            limbSwingAmount = Math.min(entity.walkAnimation.speed(partialTick), 1.0F);
            limbSwing = entity.walkAnimation.position(partialTick);
            if (entity.isBaby()) {
                limbSwing *= 3.0F;
            }
        }
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float ageInTicks = entity.tickCount + partialTick;

        ModelAttachmentRenderer.draw(entity, attachment.modelName, poseStack, bufferSource, packedLight,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, bodyYaw, netHeadYaw, headPitch);
    }
}
