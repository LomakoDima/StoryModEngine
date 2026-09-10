package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.attachment.MaterialOverrideCapabilities;
import com.dimalab.storymodengine.common.model.attachment.MaterialOverrides;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.function.Function;

/**
 * Draws a glTF model in place of a vanilla entity model, as a render layer — so it runs inside the
 * {@code PoseStack} {@code LivingEntityRenderer} has already rotated, and inherits every behaviour
 * that renderer implements (see {@link GltfEntityModel} for the full list).
 *
 * <p>Two things this gets from vanilla rather than recomputing:
 * <ul>
 *   <li><b>The hurt flash.</b> {@code overlay} is {@link LivingEntityRenderer#getOverlayCoords},
 *       which turns red while {@code hurtTime > 0}. Passing {@code OverlayTexture.NO_OVERLAY} — as
 *       the first version did — is precisely why a damaged mob never flashed.</li>
 *   <li><b>Head aim.</b> {@code netHeadYaw}/{@code headPitch} arrive already interpolated, clamped
 *       and corrected for riding and upside-down entities.</li>
 * </ul>
 */
public class GltfModelLayer<T extends LivingEntity> extends RenderLayer<T, GltfEntityModel<T>> {

    /** Where vanilla puts the model's feet, and the flip it applies; both are undone below. */
    private static final float VANILLA_MODEL_Y_OFFSET = 1.501F;
    // Reused instead of `new Quaternionf()` per held-item render call, per entity, per frame — render
    // dispatch is single-threaded, so one shared scratch instance is safe.
    private static final Quaternionf heldItemRotationScratch = new Quaternionf();

    private final ModelRenderer modelRenderer = new ModelRenderer();
    private final ModelInstanceStore instances = new ModelInstanceStore();
    private final ItemInHandRenderer itemInHandRenderer;
    private final Function<T, String> modelNameOf;
    private final Function<T, String> skinOwnerOf;
    private final HumanoidPoseSource poseSource;

    /** Supplies the procedural pose for a model that carries no animation clips of its own. */
    @FunctionalInterface
    public interface HumanoidPoseSource {
        void pose(ModelInstance instance, LivingEntity entity, float limbSwing, float limbSwingAmount,
                  float ageInTicks, float netHeadYaw, float headPitch, float partialTick);
    }

    /**
     * @param modelNameOf which model this entity wears, asked per entity rather than fixed once. That
     *                    is what lets one renderer serve every character: an earlier version took a
     *                    constant name, so a second character needed a second renderer class.
     * @param skinOwnerOf a real player's name/UUID to render this entity's {@code "skin"} material
     *                    as, or empty for none — see {@link PlayerSkinSource}.
     */
    public GltfModelLayer(RenderLayerParent<T, GltfEntityModel<T>> parent, ItemInHandRenderer itemInHandRenderer,
                          Function<T, String> modelNameOf, Function<T, String> skinOwnerOf, HumanoidPoseSource poseSource) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
        this.modelNameOf = modelNameOf;
        this.skinOwnerOf = skinOwnerOf;
        this.poseSource = poseSource;
    }

    /**
     * Resolves {@code entity}'s current {@link ModelInstance} the same way {@link #render} does,
     * without drawing anything — lets a renderer's own {@code shouldRender} override test the model's
     * real, live pose against the frustum (see {@code ModelInstance#worldCullingBox}) instead of
     * vanilla's fixed hitbox. Safe to call every frame independent of {@link #render}: {@link
     * ModelInstanceStore#instanceFor} is idempotent, so this never rebuilds an instance {@link
     * #render} is about to use unless the model genuinely changed underneath it.
     *
     * <p>Resolves through {@link LazyModelLoader#resolveOrRequestByName} rather than {@code
     * ModelPhysics.resolve} — see that method's own doc for why the render path specifically must not
     * fall back to a synchronous classpath parse. A model not yet loaded simply returns {@code null}
     * here (this frame draws nothing for it, same as any other "no definition yet" case this class
     * already handled) while the request it kicks off finishes asynchronously.
     */
    public ModelInstance instanceFor(T entity) {
        String modelName = modelNameOf.apply(entity);
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }
        ModelDefinition definition = LazyModelLoader.resolveOrRequestByName(modelName);
        return instances.instanceFor(entity, definition);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        String modelName = modelNameOf.apply(entity);
        if (modelName == null || modelName.isEmpty()) {
            return;
        }
        String skinOwner = skinOwnerOf.apply(entity);
        draw(modelRenderer, instances, itemInHandRenderer, entity, modelName, skinOwner == null ? "" : skinOwner, poseSource,
                poseStack, bufferSource, packedLight, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }

    /**
     * The actual drawing logic, extracted so it can be driven from somewhere other than the {@code
     * RenderLayer}/{@code addLayer} framework — specifically {@code
     * mixin.client.LivingEntityRendererMixin}, which intercepts a vanilla {@code
     * LivingEntityRenderer.render(...)} call for an entity that isn't wearing this engine's own
     * renderer at all (a zombie with a generic {@code ModelAttachment}, say). That call site has no
     * {@code RenderLayerParent} to hand a real {@link GltfModelLayer} instance, and doesn't need
     * one — everything this method touches ({@code modelRenderer}/{@code instances}/{@code
     * itemInHandRenderer}/{@code poseSource}) is plain state a caller can own independently. {@link
     * #render} above is now just "resolve this instance's own two {@code Function}s, then call this."
     *
     * <p>Package-private, not {@code private} — the Mixin handler lives in a different package.
     */
    static void draw(ModelRenderer modelRenderer, ModelInstanceStore instances, ItemInHandRenderer itemInHandRenderer,
                     LivingEntity entity, String modelName, String skinOwner, HumanoidPoseSource poseSource,
                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                     float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                     float netHeadYaw, float headPitch) {
        ModelDefinition definition = LazyModelLoader.resolveOrRequestByName(modelName);
        ModelInstance instance = instances.instanceFor(entity, definition);
        if (instance == null) {
            return;
        }

        // Populates AnimEvalContext's entity/movement/head-aim fields for this frame's expressions —
        // the one place this happens for every GltfModelLayer consumer (today just NpcRenderer).
        AnimEvalContext animContext = instance.animContext();
        animContext.entity = entity;
        animContext.partialTick = partialTick;
        animContext.bodyYaw = entity.yBodyRot;
        animContext.headYaw = netHeadYaw;
        animContext.headPitch = headPitch;
        // netHeadYaw is already yHeadRot - yBodyRot by the time it reaches a RenderLayer (see
        // LivingEntityRenderer.render(), which computes it that way before calling model.setupAnim
        // and every layer's render() with the same value) — verified against the decompiled source,
        // not assumed. Subtracting entity.yBodyRot from it again here (an earlier version of this line
        // did) double-counts the body yaw, producing yHeadRot - 2*yBodyRot: an error that grows with
        // the NPC's own absolute facing in the world, which is exactly why the look-procedural layer
        // that reads this field looked "crooked" relative to the world rather than merely inaccurate
        // toward the player.
        animContext.headBodyYawDelta = Mth.wrapDegrees(netHeadYaw);
        // Real ground speed from this tick's own position delta — the exact quantity vanilla's own
        // LivingEntity.calculateEntityAnimation uses, so it is valid client-side for a remotely
        // rendered entity (xo/zo track the lerped position every tick) without depending on
        // getDeltaMovement, which is not reliably replicated for a server-driven mob.
        //
        // The two fields are deliberately in DIFFERENT units, matching HollowEngine's own preset
        // contract, because the shipped standard_player preset consumes them differently:
        //   * horizontal_speed        — blocks/tick, so the preset's `> 0.02` dead zone is meaningful;
        //   * movement_animation_speed — blocks/second, because walk/run states scale playback by
        //     `movement_animation_speed / 2.0` and expect ~4.3 while walking.
        // An earlier version fed limbSwingAmount to both. That value is min(blocksPerTick*4, 1)
        // smoothed and CLAMPED AT 1.0 (verified in WalkAnimationState/updateWalkAnimation), so the
        // playback rate came out ~0.43 instead of ~2.15 — the walk cycle ran at roughly a fifth
        // speed, and sprinting could not be told apart from walking because the value saturates.
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        float blocksPerTick = (float) Math.sqrt(dx * dx + dz * dz);
        animContext.horizontalSpeed = blocksPerTick;
        animContext.signedHorizontalSpeed = blocksPerTick * 20f;

        // Found during this rewrite, not obvious from the ask: this call used to be instance.poseWith(...)
        // with NO delta-time advance at all — instance.advance(deltaSeconds) was only ever called by
        // ModelEntityRenderer/ModelDebugRenderStage, never the NPC path. That meant an NPC's clip time
        // never actually moved past whatever play() last set it to, invisible until fade/timer
        // machinery (this whole animator rewrite) started depending on a real clock. instances (used
        // above for instanceFor) already exposes deltaSeconds(entity) — already used identically by
        // ModelEntityRenderer — so this is a one-line fix rather than a new dependency.
        if (shouldFullyPose(entity)) {
            instance.advanceAndPoseWith(instances.deltaSeconds(entity, ageInTicks),
                    () -> poseSource.pose(instance, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick));
        }
        // A throttled frame simply skips the block above — the entity renders with whatever pose its
        // last full update left it in (never garbage: ModelInstance's own constructor poses once, so
        // there is always a valid pose to fall back to). See shouldFullyPose's own doc.

        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);

        poseStack.pushPose();
        // Undo the space LivingEntityRenderer set up for a vanilla model — it drops the origin to
        // 1.501 below the feet and flips X/Y — leaving the entity's own rotated frame, Y up, feet at
        // zero, which is how an imported model is authored. Order matters: the translate must be
        // cancelled before the flip, since it was applied after it.
        poseStack.translate(0.0F, VANILLA_MODEL_Y_OFFSET, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        MaterialOverrides overrides = Capabilities.get(entity, MaterialOverrideCapabilities.OVERRIDES);
        modelRenderer.render(instance, poseStack, bufferSource, packedLight, overlay, skinOwner == null ? "" : skinOwner,
                overrides == null ? Map.of() : overrides.byName);
        renderHeldItem(itemInHandRenderer, entity, instance, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static final double LOD_NEAR_DISTANCE_SQ = 16.0 * 16.0;
    private static final double LOD_MID_DISTANCE_SQ = 32.0 * 32.0;

    /**
     * Animation-update LOD, the concrete answer to "100+ scripted NPCs in one town" — the scenario
     * this whole mechanism was built to answer. Within {@link #LOD_NEAR_DISTANCE_SQ} of the camera,
     * an entity poses every frame, full fidelity, since that's where a player would actually notice a
     * stutter. Beyond it, only every 2nd frame (out to {@link #LOD_MID_DISTANCE_SQ}) or every 3rd
     * frame (further still) actually recomputes a pose — {@code drawTimed} simply skips the {@code
     * advanceAndPoseWith} call on a throttled frame, leaving the entity rendering with whatever pose
     * it last computed. That's never garbage (see {@code drawTimed}'s own call site), just a "held"
     * frame — the same animation-LOD technique background characters get in most engines, since a
     * distant NPC's walk cycle updating at 30 or 20fps instead of 60+ is not something a player can
     * actually perceive from that far away, while the CPU cost of computing it every single frame is
     * identical to a nearby, fully-visible one.
     *
     * <p>The {@code (frame + entity.getId())} stagger is what keeps this from reading as a
     * synchronized stutter: without it, every throttled entity would update on the exact same frames
     * as every other one at the same tier, which — for a whole crowd sharing one distance band — would
     * look like the entire crowd freezing and un-freezing in lockstep rather than each individual NPC
     * moving a little less smoothly. Offsetting by id spreads updates evenly across frames instead.
     *
     * <p>Thresholds are deliberately conservative (16/32 blocks, only 2x/3x slower, never fully
     * stopped) — a model actively being interacted with (dialogue, combat, standing right next to the
     * player) is well inside 16 blocks and always gets full-rate posing; only genuinely background
     * NPCs are throttled at all.
     */
    private static boolean shouldFullyPose(LivingEntity entity) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distanceSq = entity.distanceToSqr(cameraPos.x, cameraPos.y, cameraPos.z);
        if (distanceSq <= LOD_NEAR_DISTANCE_SQ) {
            return true;
        }
        int rate = distanceSq <= LOD_MID_DISTANCE_SQ ? 2 : 3;
        return (RenderFrameClock.currentFrame() + entity.getId()) % rate == 0;
    }

    /**
     * Puts the held item where the model itself says to — a dedicated empty node named {@link
     * ItemAnchor#RIGHT_HAND_ITEM_NODE}, the same convention HollowEngine's {@code ItemNode} uses.
     * Its world matrix already <em>is</em> the item's placement (position, rotation, scale, all
     * authored by whoever rigged the model in Blockbench), so nothing here measures or guesses
     * anything about a particular model's hand geometry — the one correction applied,
     * {@link ItemAnchor#CORRECTION_PITCH_DEGREES}, only reconciles glTF's item-forward axis with
     * what {@code ItemRenderer} expects, and is the same for every model.
     *
     * <p>Falls back to the old hand-bone-plus-measured-offset placement only when a model has no
     * such node, so a model authored before this convention existed doesn't simply lose its held
     * item. That fallback is legacy: fixing a model for real means adding the anchor node, not
     * tuning the fallback's numbers.
     */
    private static void renderHeldItem(ItemInHandRenderer itemInHandRenderer, LivingEntity entity, ModelInstance instance,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ItemStack held = entity.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        RuntimeNode anchor = instance.nodesByName().get(ItemAnchor.RIGHT_HAND_ITEM_NODE);
        poseStack.pushPose();
        if (anchor != null) {
            poseStack.mulPoseMatrix(anchor.globalMatrix());
            poseStack.mulPose(heldItemRotationScratch.identity().rotateX((float) Math.toRadians(ItemAnchor.CORRECTION_PITCH_DEGREES)));
        } else {
            RuntimeNode hand = instance.nodesByName().get(ItemAnchor.LEGACY_RIGHT_HAND_BONE);
            if (hand == null) {
                poseStack.popPose();
                return;
            }
            poseStack.mulPoseMatrix(hand.globalMatrix());
            poseStack.translate(0.0F, ItemAnchor.LEGACY_FIST_OFFSET_Y, 0.0F);
            poseStack.mulPose(heldItemRotationScratch.identity().rotateX((float) Math.toRadians(ItemAnchor.LEGACY_PITCH_DEGREES)));
            poseStack.mulPose(heldItemRotationScratch.identity().rotateY((float) Math.toRadians(ItemAnchor.LEGACY_YAW_DEGREES)));
        }
        itemInHandRenderer.renderItem(entity, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    /**
     * Where a held item sits, and the one model-independent correction needed to draw it there.
     */
    public static final class ItemAnchor {
        /** An empty node the model rigger places at the grip, oriented however the item should sit. */
        public static final String RIGHT_HAND_ITEM_NODE = "RightHandItem";
        public static final String LEFT_HAND_ITEM_NODE = "LeftHandItem";
        /**
         * Reconciles glTF's item-forward axis with {@code ItemRenderer}'s own convention — verified
         * against HollowEngine's {@code ItemNode}, which applies the same fixed correction.
         */
        public static final float CORRECTION_PITCH_DEGREES = -90.0F;

        /** Legacy fallback for a model with no {@link #RIGHT_HAND_ITEM_NODE} — see the class doc. */
        public static final String LEGACY_RIGHT_HAND_BONE = "rightHand";
        public static final float LEGACY_FIST_OFFSET_Y = -0.4375F;
        public static final float LEGACY_PITCH_DEGREES = -90.0F;
        public static final float LEGACY_YAW_DEGREES = 180.0F;

        private ItemAnchor() {
        }
    }
}
