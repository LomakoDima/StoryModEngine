package com.dimalab.storymodengine.common.model;

import com.dimalab.storymodengine.common.model.rig.RigBone;

import java.util.List;
import java.util.Map;

/**
 * What a {@code .smemeta} sidecar says about the model it sits next to — everything the engine needs
 * to know that isn't in the glTF file itself.
 *
 * <p><b>Why a sidecar at all.</b> The rig used to live in Java, naming one specific asset's parts
 * ({@code golova}, {@code Helmet}, {@code leftHand_layer}) inside engine code, so a second model with
 * different part names meant editing and recompiling the engine. A description that belongs to the
 * asset belongs <i>with</i> the asset. HollowEngine reaches the same conclusion with its own
 * {@code .hemeta} files; the format here is JSON rather than TOML because every other data file this
 * engine loads is JSON through Gson, and adding a second parser would buy nothing.
 *
 * <p>A model with no sidecar loads exactly as before — {@link #EMPTY} means "no rig, detect facing
 * from the exporter", which is the right default for a model that already has its own hierarchy.
 *
 * <p>{@link #aliases} solves a different problem than {@link #rig}: a rig rebuilds a <i>flat</i>
 * export (no grouping at all) into a real hierarchy from scratch. A model that already has real
 * hierarchy — its own tool's groups, correctly nested — needs none of that; it just names its pivot
 * groups however reads naturally in that tool ({@code "LeftArm"}, {@code "Body"}), not in the fixed
 * vocabulary {@code HumanoidBones} animates by. Aliasing relabels those existing nodes in place —
 * nothing about the tree changes — rather than discarding a correct hierarchy to rebuild an
 * equivalent one through {@link #rig}.
 *
 * @param disableInstanceCulling {@code "disableCulling": true} — opts a model out of {@code
 * client.model.ModelInstance#worldCullingBox}'s live per-node AABB (see {@code
 * client.entity.NpcRenderer#shouldRender}), falling back to vanilla's own hitbox-based culling
 * instead. For a model whose per-node bounds accumulation would be misleading — detached decorative
 * geometry far from the model's real silhouette, say — not for "never cull this at all" (that's the
 * separate, already-existing {@code Entity#noCulling}).
 * @param hitboxExcludeNodes {@code "hitboxExcludeNodes": ["LeftArm", "RightArm"]} — node names (this
 * model's own, whatever it actually calls them — no fixed vocabulary assumed) whose entire subtree is
 * left out of {@code common.model.physics.ModelBounds#footprint}, the measurement an entity's hitbox
 * width is derived from. Exists because a humanoid's bind-pose arms are typically spread wide (a
 * T-/A-pose), which would otherwise make the hitbox as wide as the wingspan; empty (the default) means
 * nothing is excluded — the same full-geometry measurement as before this existed.
 * @param forceRenderPath {@code "forceRenderPath": "pipeline"} — pins {@code
 * ModelDefinition#renderPath()} to a fixed value instead of letting it compute one from the model's
 * own primitive count/density. {@code null} (the default) means "use the automatic heuristic." Exists
 * because that heuristic is a one-time, per-model decision that can't know how many entities will ever
 * wear this model at once: a humanoid built from dozens of small per-limb primitives trips the
 * heuristic's "many small primitives → BATCHING" rule, which is the right call for one instance
 * standing alone but the wrong one for a hundred NPCs sharing it, where {@code PIPELINE}'s instancing
 * collapses N entities × M primitives into just M draw calls total regardless of N. A script/asset
 * author who knows a model is meant to be worn by a crowd (an NPC humanoid, say) can override the
 * guess directly rather than accepting whichever path the primitive-counting heuristic happens to land
 * on for that specific mesh.
 */
public record ModelMetadata(List<RigBone> rig, Facing facing, Map<String, String> aliases,
                             Integer skinMaterialIndex, String animationController,
                             boolean disableInstanceCulling, List<String> hitboxExcludeNodes,
                             RenderPath forceRenderPath) {

    public static final ModelMetadata EMPTY = new ModelMetadata(List.of(), Facing.AUTO, Map.of(), null, null, false, List.of(), null);

    /** The file suffix appended to a model's own name: {@code player.glb} → {@code player.glb.smemeta}. */
    public static final String EXTENSION = ".smemeta";

    /**
     * Which way the source model considers "forward".
     *
     * <p>{@link #AUTO} reads it from {@code asset.generator}, which handles the common cases on its
     * own. The explicit values exist because that detection is a heuristic over a free-text field: an
     * exporter this engine has never heard of, or a model re-exported through a converter that
     * rewrites the generator string, can be told the answer instead of being guessed at.
     */
    public enum Facing {
        AUTO,
        /** glTF's own convention: front at +Z, so the model is turned to match Minecraft. */
        GLTF,
        /** Already authored the way a vanilla model is; no correction. */
        MINECRAFT
    }

    public boolean hasRig() {
        return !rig.isEmpty();
    }

    public boolean hasAliases() {
        return !aliases.isEmpty();
    }

    /** Whether this model's sidecar designates a material (by 0-based glTF index) to render as {@code MaterialData.SKIN_MATERIAL_NAME} — see {@code GltfMaterialImporter}. */
    public boolean hasSkinMaterialOverride() {
        return skinMaterialIndex != null;
    }

    /** Whether this model's sidecar names a Java-registered animator preset — see {@code client.model.animator.AnimatorPresets}. */
    public boolean hasAnimationController() {
        return animationController != null && !animationController.isEmpty();
    }

    public boolean hasHitboxExcludeNodes() {
        return !hitboxExcludeNodes.isEmpty();
    }

    /** Whether this model's sidecar pins a fixed render path instead of leaving it to the automatic heuristic — see {@link #forceRenderPath}'s own doc. */
    public boolean hasForceRenderPath() {
        return forceRenderPath != null;
    }
}
