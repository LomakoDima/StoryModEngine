package com.dimalab.storymodengine.client.entity;

import com.dimalab.storymodengine.api.entity.NpcAnimationMode;
import com.dimalab.storymodengine.client.model.GltfEntityModel;
import com.dimalab.storymodengine.client.model.GltfModelLayer;
import com.dimalab.storymodengine.client.model.HumanoidPoser;
import com.dimalab.storymodengine.client.model.ModelInstance;
import com.dimalab.storymodengine.client.model.animator.AnimationLayer;
import com.dimalab.storymodengine.client.model.animator.AnimationPlayMode;
import com.dimalab.storymodengine.client.model.animator.ClipAnimationLayerSpec;
import com.dimalab.storymodengine.client.model.animator.ClipLayer;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import com.dimalab.storymodengine.common.model.attachment.ModelAttachCapabilities;
import com.dimalab.storymodengine.common.model.attachment.ModelAttachment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Draws any {@link NpcEntity}, whatever model it happens to wear.
 *
 * <p>The renderer asks the entity which model to use rather than naming one — that is the whole
 * difference from the mob it replaces, which had its model compiled in and so needed a new renderer
 * class per character.
 *
 * <p>A {@link LivingEntityRenderer} over a do-nothing {@link GltfEntityModel}: all of a living
 * entity's visible behaviour — the red flash when hurt, toppling on death, invisibility, name tag,
 * shadow, fire, leash — lives in that renderer, and the glTF geometry is drawn by {@link
 * GltfModelLayer} inside the {@code PoseStack} it has already set up.
 */
public class NpcRenderer extends LivingEntityRenderer<NpcEntity, GltfEntityModel<NpcEntity>> {

    /** Only what {@code EntityRenderer} demands; every material binds its own texture. */
    private static final ResourceLocation TEXTURE = new ResourceLocation(StoryModEngine.MODID, "model/white");

    /**
     * Above the standard player preset's own layers ({@code standard_player:locomotion} at 0,
     * {@code standard_player:look_procedural} at 20) so a manually-commanded gesture (see {@code
     * /sme npc animation}) fully overrides the controller's own pose while it's set,
     * rather than being overridden by it.
     */
    private static final int MANUAL_OVERRIDE_PRIORITY = 100;

    /** Kept as a field (not just passed to {@code addLayer}) so {@link #shouldRender} can reach the same {@link ModelInstance} {@link #poseNpc} renders, via {@link GltfModelLayer#instanceFor}. */
    private final GltfModelLayer<NpcEntity> modelLayer;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new GltfEntityModel<>(), 0.5f);
        this.modelLayer = new GltfModelLayer<>(this, context.getItemInHandRenderer(), NpcRenderer::modelNameOf, NpcEntity::skinOwner, this::poseNpc);
        addLayer(modelLayer);
    }

    /**
     * {@code npc.modelName()} first — NPC's own dedicated, {@code SynchedEntityData}-backed field,
     * unchanged from before this method existed — falling back to the generic {@code ModelAttachment}
     * capability only when that's empty. Real unification, not just a shared name: an NPC spawned
     * with no model of its own (an odd but legal state) can still be given one through the exact same
     * {@code /sme model attach} command any other entity uses, without this class needing its own
     * copy of that command or migrating its own field onto the capability (see {@code
     * ModelAttachment}'s own doc for why that migration isn't worth the risk).
     */
    private static String modelNameOf(NpcEntity npc) {
        String own = npc.modelName();
        if (!own.isEmpty()) {
            return own;
        }
        ModelAttachment attachment = Capabilities.get(npc, ModelAttachCapabilities.MODEL_ATTACHMENT);
        return attachment != null ? attachment.modelName : "";
    }

    /**
     * Plays the clip the NPC has been told to play, if its model has one, and always applies the
     * procedural humanoid motion on top — walk, idle sway, head aim and the attack swing. The shipped
     * models carry no clips at all, so without the procedural half an NPC would simply stand frozen;
     * with it, a model that <i>does</i> have clips still gets them, composed together by {@code
     * ModelInstance.poseWith}.
     */
    private void poseNpc(ModelInstance instance, net.minecraft.world.entity.LivingEntity entity,
                         float limbSwing, float limbSwingAmount, float ageInTicks,
                         float netHeadYaw, float headPitch, float partialTick) {
        // A model with its own attached controller (see AnimatorPresets/ModelInstance) drives its own
        // locomotion FSM and look-procedural layer from movement state; running HumanoidPoser on top
        // would fight it for the same bones (two head-aim systems, two walk cycles). /sme
        // npc animation still works for such a model — see applyManualOverride — it just layers a
        // one-off gesture on top of the controller instead of replacing the whole stack.
        if (entity instanceof NpcEntity npc) {
            applyClipOverride(instance, npc);
        }
        if (instance.definition().metadata().hasAnimationController()) {
            return;
        }
        HumanoidPoser.pose(instance, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                entity.getAttackAnim(partialTick));
    }

    /**
     * {@code npc_play_once}/{@code npc_play_freeze}/{@code npc_play_looped}/{@code
     * npc_stop_animation} (see {@code NpcScriptCommands}) — one {@link #MANUAL_OVERRIDE_PRIORITY}
     * clip layer at {@link ModelInstance#PLAY_LAYER_ID}, built directly rather than through {@link
     * ModelInstance#play} (which only ever freezes a non-looping clip, and would {@code
     * animator.clear()} a controller-driven model's own layers along with everything else). Shared by
     * both a controller-driven model (layered on top of its locomotion/look layers) and a plain one
     * (layered underneath {@code HumanoidPoser}'s own bone pokes) — same spec either way.
     */
    private void applyClipOverride(ModelInstance instance, NpcEntity npc) {
        String clip = npc.animation();
        AnimationLayer current = instance.animator().layer(ModelInstance.PLAY_LAYER_ID);
        boolean playing = !clip.isEmpty() && clip.equals(current != null ? current.clipName() : null);
        if (!clip.isEmpty() && !playing) {
            // Remove any previous override first — leaving it in place while adding a new one would
            // stack duplicate "play"-id layers (layer(id) only ever returns the first), which then
            // never compares equal to a newly commanded clip and re-adds another one every frame.
            instance.animator().removeLayer(ModelInstance.PLAY_LAYER_ID);
            NpcAnimationMode mode = npc.animationMode();
            ClipAnimationLayerSpec spec = ClipAnimationLayerSpec.of(ModelInstance.PLAY_LAYER_ID, clip)
                    .withPlayMode(toPlayMode(mode))
                    .withPriority(MANUAL_OVERRIDE_PRIORITY)
                    .withRemoveOnEnd(mode == NpcAnimationMode.ONCE);
            instance.animator().addLayer(new ClipLayer(spec));
        } else if (clip.isEmpty() && current != null) {
            instance.animator().removeLayer(ModelInstance.PLAY_LAYER_ID);
        }
    }

    private static AnimationPlayMode toPlayMode(NpcAnimationMode mode) {
        return switch (mode) {
            case ONCE -> AnimationPlayMode.ONCE;
            case FREEZE -> AnimationPlayMode.CLAMP_FOREVER;
            case LOOP -> AnimationPlayMode.LOOP;
        };
    }

    /**
     * Null so vanilla never draws the placeholder body — the layer draws everything real. The one
     * behaviour this gives up is the glowing outline, which vanilla produces by swapping this render
     * type; a layer's geometry is not part of that pass.
     */
    @Nullable
    @Override
    protected RenderType getRenderType(NpcEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return null;
    }

    /**
     * Vanilla's own {@code EntityRenderer.shouldRender} — which this otherwise reproduces exactly —
     * culls against a box built from {@link net.minecraft.world.entity.Entity#getBoundingBoxForCulling()},
     * i.e. the entity's hitbox. For most entities that's the model too, but this engine's NPCs can
     * play a clip that swings a limb well past their (fixed-size) hitbox — an attack, a wide gesture
     * — so a pose-independent hitbox box can under-cull-safely but also, worse, over-cull: clip a
     * model whose current pose reaches into view while vanilla's box says it hasn't.
     *
     * <p>{@link ModelInstance#worldCullingBox} fixes that with the model's actual live pose (every
     * meshed node's own extent, transformed by its current {@code globalMatrix()}), not just the whole
     * model's bind-pose box rotated by entity yaw — the same effect HollowEngine reaches by wrapping
     * {@code Frustum.isVisible} instead. The entity-to-world transform below reproduces {@code
     * LivingEntityRenderer.setupRotations}'s own {@code mulPose(Axis.YP.rotationDegrees(180 -
     * bodyRot))} exactly (verified against the decompiled source) — the same rotation {@code
     * ModelRenderer} itself poses against, just rebuilt here since {@code shouldRender} runs before any
     * {@code PoseStack} exists for this entity this frame. It deliberately does not special-case
     * vanilla's sleeping/dying/spin-attack/upside-down pose branches (same method, further down) — an
     * NPC does not use any of those vanilla poses today, and a culling test being slightly off for one
     * of them would cost at most one frame's worth of imprecise culling, not a crash.
     */
    // Reused instead of `new Matrix4f()` per entity per frame — NpcRenderer is one shared instance
    // per entity type (not per entity), so this is safe: render dispatch is single-threaded and
    // never reenters this method for the same renderer before it returns.
    private final Matrix4f cullingWorldScratch = new Matrix4f();

    @Override
    public boolean shouldRender(NpcEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        if (entity.noCulling) {
            return true;
        }
        ModelInstance instance = modelLayer.instanceFor(entity);
        if (instance == null || instance.definition().metadata().disableInstanceCulling()) {
            return super.shouldRender(entity, frustum, camX, camY, camZ);
        }
        Matrix4f entityWorld = cullingWorldScratch.identity()
                .translate((float) entity.getX(), (float) entity.getY(), (float) entity.getZ())
                .rotateY((float) Math.toRadians(180.0f - entity.yBodyRot));
        AABB box = instance.worldCullingBox(entityWorld);
        if (box == null) {
            return super.shouldRender(entity, frustum, camX, camY, camZ);
        }
        return frustum.isVisible(box.inflate(0.1D));
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return TEXTURE;
    }

    /**
     * Vanilla's own {@code LivingEntityRenderer.setupRotations} unconditionally rolls a dying entity
     * {@code f * getFlipDegrees(entity)} degrees around Z as {@code deathTime} climbs (verified against
     * the decompiled source) — the entire "topples sideways" death visual a plain zombie or a real
     * player has, since neither one has any baked death <i>animation</i> to speak of; that generic roll
     * is standing in for one. A model with a real animation controller already plays an authored
     * {@code death} clip (see {@code StandardPlayerAnimatorPreset}'s own "death" state) — leaving
     * vanilla's roll on top of that layers a second, unrelated sideways tilt onto a collapse the clip
     * already poses correctly, which is exactly what read as "dies crooked/on its side" (swapping a
     * plain zombie onto this same rig has no clip and no controller, so only vanilla's own roll ever
     * ran, and looked fine for exactly that reason). Zeroing the flip angle only when a controller is
     * actually driving the pose leaves a clip-less model's fallback death roll untouched.
     */
    @Override
    protected float getFlipDegrees(NpcEntity entity) {
        ModelInstance instance = modelLayer.instanceFor(entity);
        if (instance != null && instance.definition().metadata().hasAnimationController()) {
            return 0.0F;
        }
        return super.getFlipDegrees(entity);
    }
}
