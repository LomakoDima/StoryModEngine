package com.dimalab.storymodengine.client.model;

import com.dimalab.storymodengine.api.model.LayerBlendMode;
import com.dimalab.storymodengine.client.model.animator.AnimationLayer;
import com.dimalab.storymodengine.client.model.animator.AnimationPlayMode;
import com.dimalab.storymodengine.client.model.animator.AnimatorLayerSpec;
import com.dimalab.storymodengine.client.model.animator.AnimatorPresets;
import com.dimalab.storymodengine.client.model.animator.ClipAnimationLayerSpec;
import com.dimalab.storymodengine.client.model.animator.ClipLayer;
import com.dimalab.storymodengine.client.model.animator.PoseTarget;
import com.dimalab.storymodengine.client.model.animator.expr.AnimEvalContext;
import com.dimalab.storymodengine.client.model.gpu.GpuSkinBuffers;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.Skin;
import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One model being shown somewhere: the shared {@link ModelDefinition} plus this instance's own
 * {@link RuntimeNode} tree and layer stack. Two entities showing the same model each get their own
 * {@code ModelInstance} and pose independently.
 */
public final class ModelInstance {

    /** The layer id {@link #play} uses — lets a caller ask {@code animator().layer(PLAY_LAYER_ID)} what's currently playing without holding onto the {@link AnimationLayer} reference itself. */
    public static final String PLAY_LAYER_ID = "play";

    private final ModelDefinition definition;
    private final List<RuntimeNode> roots = new ArrayList<>();
    private final Map<Integer, RuntimeNode> nodesByIndex = new LinkedHashMap<>();
    /** "First node with this name, by declaration order" — the same {@code putIfAbsent} semantics {@code HumanoidPoser}'s own bone lookup and {@code GltfModelLayer.findNode} each used to independently rebuild from scratch, once per frame, per caller. Node identity is fixed for this instance's whole lifetime, so this is built once here and never invalidated. */
    private final Map<String, RuntimeNode> nodesByName = new LinkedHashMap<>();
    private final ModelAnimator animator = new ModelAnimator();
    private final AnimEvalContext animContext = new AnimEvalContext();
    private final PoseTarget poseTarget;
    /** One {@link GpuSkinBuffers} per skinned primitive this instance draws — skin matrices and morph weights are per-entity, so these can't be shared across instances of the same model. */
    private final Map<Primitive, GpuSkinBuffers> gpuSkinBuffers = new IdentityHashMap<>();

    /** Sentinel for "never posed" — guaranteed not to equal a real {@link RenderFrameClock} value, which starts at 0 and only increases. */
    private static final long UNPOSED_FRAME = -1L;
    /** The {@link RenderFrameClock} frame this instance last actually advanced/posed on — see {@link #beginFrame}. */
    private long posedFrame = UNPOSED_FRAME;

    // Reused across worldCullingBox() calls instead of allocating 3 Vector3f + 1 Matrix4f fresh every
    // call — every live-culled entity hits this once per frame, so the allocation volume scales with
    // exactly the entity count culling exists to make cheap.
    private final Vector3f cullingWorldMin = new Vector3f();
    private final Vector3f cullingWorldMax = new Vector3f();
    private final Vector3f cullingCorner = new Vector3f();
    private final Matrix4f cullingCombined = new Matrix4f();

    public ModelInstance(ModelDefinition definition) {
        this.definition = definition;
        for (ModelNode root : definition.roots()) {
            RuntimeNode runtimeRoot = new RuntimeNode(root, null);
            roots.add(runtimeRoot);
            runtimeRoot.index(nodesByIndex);
        }
        for (RuntimeNode node : nodesByIndex.values()) {
            nodesByName.putIfAbsent(node.definition().name(), node);
        }
        Map<String, AnimationClip> animationsByName = new LinkedHashMap<>();
        for (AnimationClip clip : definition.animations()) {
            animationsByName.put(clip.name(), clip);
        }
        this.poseTarget = new PoseTarget(nodesByIndex, animationsByName);
        if (definition.metadata().hasAnimationController()) {
            for (AnimatorLayerSpec spec : AnimatorPresets.get(definition.metadata().animationController())) {
                animator.addLayer(AnimationLayer.forSpec(spec));
            }
        }
        pose();
    }

    /** The per-frame expression-evaluation state (entity, time, movement, custom variables) this instance's layers read from — populated by whoever poses the model (see {@code GltfModelLayer}) before advancing/posing. */
    public AnimEvalContext animContext() {
        return animContext;
    }

    public ModelDefinition definition() {
        return definition;
    }

    public List<RuntimeNode> roots() {
        return roots;
    }

    public Map<Integer, RuntimeNode> nodesByIndex() {
        return nodesByIndex;
    }

    /** First node with a given name, by declaration order — see the field's own doc. */
    public Map<String, RuntimeNode> nodesByName() {
        return nodesByName;
    }

    /**
     * A real, pose-aware world-space AABB for this instance right now — accumulated per meshed node
     * from its own local extent ({@link ModelPhysics#perNodeBounds}), transformed by that node's
     * <i>live</i> {@link RuntimeNode#globalMatrix()} (model space) and then {@code entityWorld} (this
     * instance's world placement — translate + yaw, the same combination {@code
     * ModelRenderer#submitInstanced} already does for rendering: {@code entityWorld.mul(nodeMatrix)}).
     * Unlike {@link ModelBounds#toWorldCullingBox}, which sizes one box for the bind pose and rotates
     * it only by entity yaw, this reflects the model's actual current pose — an arm swung wide moves
     * its own node's box with it.
     *
     * <p>Valid as of whichever pose the last {@link #pose()}/{@link #poseWith} call left this instance
     * in — at most one frame stale, the same tradeoff {@link RuntimeNode#globalMatrix()} itself already
     * carries, and cheap for the same reason: no vertex scan, just 8 corners per meshed node.
     *
     * @return {@code null} if this model has no meshed node at all — the caller falls back to vanilla
     * culling for that case, same as an empty {@link ModelBounds}.
     */
    public AABB worldCullingBox(Matrix4f entityWorld) {
        Map<Integer, ModelBounds> perNode = ModelPhysics.perNodeBounds(definition);
        Vector3f worldMin = cullingWorldMin.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f worldMax = cullingWorldMax.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        Vector3f corner = cullingCorner;
        Matrix4f combined = cullingCombined;
        boolean any = false;
        for (RuntimeNode node : nodesByIndex.values()) {
            ModelBounds local = perNode.get(node.definition().index());
            if (local == null) {
                continue;
            }
            combined.set(entityWorld).mul(node.globalMatrix());
            for (int i = 0; i < 8; i++) {
                corner.set(
                        (i & 1) == 0 ? local.min().x : local.max().x,
                        (i & 2) == 0 ? local.min().y : local.max().y,
                        (i & 4) == 0 ? local.min().z : local.max().z);
                combined.transformPosition(corner);
                worldMin.min(corner);
                worldMax.max(corner);
                any = true;
            }
        }
        return any ? new AABB(worldMin.x, worldMin.y, worldMin.z, worldMax.x, worldMax.y, worldMax.z) : null;
    }

    public ModelAnimator animator() {
        return animator;
    }

    /** This instance's GPU skinning state for {@code primitive}, building it on first request. */
    /**
     * Self-healing against a shader recompile: {@code GpuSkinBuffers} resolves vanilla's own compiled
     * entity-cutout shader's attribute locations exactly once, at construction — a shaderpack option
     * toggle (or a plain vanilla resource/shader reload) can relink that shader to a new compiled
     * program without this instance ever finding out, which is exactly what {@code InstancedShader}
     * already guards against one level up ({@code setActiveOverride}/{@code clearActiveOverride} →
     * {@code InstanceFlush.clearCache()}) but {@code GpuSkinBuffers} never did — a real bug, not a
     * theoretical one: it explains why a skinned NPC (the only consumer of this class) stayed broken
     * after toggling a shaderpack's "Advanced Materials" option while other, non-skinned models on
     * screen at the same time rendered fine. Comparing {@link GpuSkinBuffers#builtForProgramId()}
     * against the shader's <em>current</em> id on every call is cheap (one native getter, one int
     * compare) and needs no separate per-frame watcher, registry, or Oculus-specific hook — it heals
     * itself the next time this primitive is actually drawn, Oculus/Iris installed or not.
     */
    public GpuSkinBuffers gpuSkinBuffersFor(Primitive primitive, Skin skin) {
        GpuSkinBuffers existing = gpuSkinBuffers.get(primitive);
        if (existing == null) {
            GpuSkinBuffers fresh = new GpuSkinBuffers(primitive, skin);
            gpuSkinBuffers.put(primitive, fresh);
            return fresh;
        }

        ShaderInstance entityShader = GameRenderer.getRendertypeEntityCutoutNoCullShader();
        // A transient null (shader not loaded yet) keeps the existing, still-valid buffers rather
        // than attempting a rebuild here — GpuSkinBuffers' own constructor already throws in that
        // situation on first build, so this doesn't introduce a new crash path on top of that.
        if (entityShader == null || existing.builtForProgramId() == entityShader.getId()) {
            return existing;
        }
        existing.destroy();
        GpuSkinBuffers fresh = new GpuSkinBuffers(primitive, skin);
        gpuSkinBuffers.put(primitive, fresh);
        return fresh;
    }

    /** Frees every GL resource this instance's GPU skinning holds — called when the instance itself is discarded (see {@code ModelInstanceStore}). */
    public void destroyGpuResources() {
        for (GpuSkinBuffers buffers : gpuSkinBuffers.values()) {
            buffers.destroy();
        }
        gpuSkinBuffers.clear();
    }

    /**
     * Convenience for the common single-clip case: replaces the whole layer stack with one
     * {@link LayerBlendMode#OVERRIDE} layer at {@link #PLAY_LAYER_ID}.
     *
     * <p>{@code removeOnEnd} is forced to {@code false} here — {@link ModelAnimator#applyTo} now
     * auto-drops a finished layer (matching HollowEngine), but that would silently change this
     * method's long-standing contract: a non-looping clip is meant to freeze on its last frame
     * forever, not disappear once it fades out. Callers wanting HE's own default should build a
     * {@link ClipAnimationLayerSpec} directly instead of going through this method.
     */
    public AnimationLayer play(String animationName, boolean loop) {
        animator.clear();
        AnimationClip clip = definition.animation(animationName);
        if (clip == null) {
            return null;
        }
        ClipAnimationLayerSpec spec = ClipAnimationLayerSpec.of(PLAY_LAYER_ID, animationName)
                .withPlayMode(loop ? AnimationPlayMode.LOOP : AnimationPlayMode.ONCE)
                .withRemoveOnEnd(false);
        return animator.addLayer(new ClipLayer(spec));
    }

    /**
     * Guarded by {@link #beginFrame} — a second call within the same client frame (glowing/outline
     * rendering doesn't cause this in vanilla today, verified against {@code LevelRenderer}, but this
     * engine already has three independent render paths resolving a {@code ModelInstance} through
     * their own {@code ModelInstanceStore}, and nothing stops a fourth) is a silent no-op instead of
     * double-advancing {@link AnimEvalContext#time} and redoing the whole pose pass.
     */
    public void advance(float deltaSeconds) {
        if (!beginFrame()) {
            return;
        }
        animContext.deltaTime = deltaSeconds;
        animContext.time += deltaSeconds;
        pose();
    }

    /** {@link #advance} and {@link #poseWith} in one call — for a caller that needs both the clock advance and a procedural step in the same pass (see {@code GltfModelLayer}). Guarded the same way {@link #advance} is — see its doc. */
    public void advanceAndPoseWith(float deltaSeconds, Runnable procedural) {
        if (!beginFrame()) {
            return;
        }
        animContext.deltaTime = deltaSeconds;
        animContext.time += deltaSeconds;
        poseWith(procedural);
    }

    /**
     * @return {@code true} the first time this is called for the current {@link RenderFrameClock}
     * frame, {@code false} on any repeat — deliberately not applied to {@link #pose()}/{@link
     * #poseWith}, which stay the always-runs primitive: the constructor's own bind pose and every
     * {@code ModelSelfTest} case that calls them directly depend on each call actually re-posing.
     */
    private boolean beginFrame() {
        long frame = RenderFrameClock.currentFrame();
        if (frame == posedFrame) {
            return false;
        }
        posedFrame = frame;
        return true;
    }

    /** Reset to bind pose, apply every layer, then recompute matrices — the whole per-frame posing pass. */
    public void pose() {
        poseWith(null);
    }

    /**
     * The same pass with a procedural step wedged in: reset → animation layers → {@code procedural} →
     * recompute matrices.
     *
     * <p>The ordering is what matters. Running procedural posing <i>after</i> the layers lets it
     * compose with clip-driven animation (a head that tracks the player on top of a walk cycle)
     * instead of being overwritten by it, and running it <i>before</i> the matrix pass means it costs
     * nothing extra — the hierarchy is walked once either way. See {@code HumanoidPoser}, which is
     * what makes a model carrying no animation clips at all still move.
     */
    public void poseWith(Runnable procedural) {
        animContext.temporaries.clear();
        for (RuntimeNode root : roots) {
            root.resetPoseRecursive();
        }
        animator.applyTo(poseTarget, animContext);
        if (procedural != null) {
            procedural.run();
        }
        for (RuntimeNode root : roots) {
            root.updateHierarchy();
        }
    }
}
