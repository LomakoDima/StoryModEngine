package com.dimalab.storymodengine.common.model;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One imported model — format-agnostic (nothing here or below mentions glTF; {@code
 * common.model.gltf} is the only code that reads the actual file format). Immutable and shared: one
 * instance per loaded file, and every {@code client.model.ModelInstance} playing it keeps its own
 * runtime state, never mutating this.
 */
public final class ModelDefinition {

    private final ResourceLocation id;
    private final List<ModelNode> roots;
    private final List<AnimationClip> animations;
    private final List<ModelNode> allNodes;
    private final Map<Integer, ModelNode> nodesByIndex;
    private final ModelMetadata metadata;
    private RenderPath cachedRenderPath;

    public ModelDefinition(ResourceLocation id, List<ModelNode> roots, List<AnimationClip> animations) {
        this(id, roots, animations, ModelMetadata.EMPTY);
    }

    /** @param metadata the source model's own {@code .smemeta} — carried along so a rig/alias rebuild doesn't drop it. */
    public ModelDefinition(ResourceLocation id, List<ModelNode> roots, List<AnimationClip> animations, ModelMetadata metadata) {
        this.id = id;
        this.roots = List.copyOf(roots);
        this.animations = List.copyOf(animations);
        this.metadata = metadata;

        List<ModelNode> flattened = new ArrayList<>();
        for (ModelNode root : roots) {
            flatten(root, flattened);
        }
        this.allNodes = List.copyOf(flattened);

        Map<Integer, ModelNode> byIndex = new LinkedHashMap<>();
        for (ModelNode node : flattened) {
            byIndex.put(node.index(), node);
        }
        this.nodesByIndex = Map.copyOf(byIndex);
    }

    private static void flatten(ModelNode node, List<ModelNode> out) {
        out.add(node);
        for (ModelNode child : node.children()) {
            flatten(child, out);
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public ModelMetadata metadata() {
        return metadata;
    }

    public List<ModelNode> roots() {
        return roots;
    }

    /** Every node of the whole hierarchy, flattened once at construction — parents always before their children. */
    public List<ModelNode> allNodes() {
        return allNodes;
    }

    public ModelNode node(int index) {
        return nodesByIndex.get(index);
    }

    public ModelNode nodeByName(String name) {
        for (ModelNode node : allNodes) {
            if (name.equals(node.name())) {
                return node;
            }
        }
        return null;
    }

    public List<AnimationClip> animations() {
        return animations;
    }

    public AnimationClip animation(String name) {
        for (AnimationClip clip : animations) {
            if (clip.name().equals(name)) {
                return clip;
            }
        }
        return null;
    }

    public boolean hasSkin() {
        for (ModelNode node : allNodes) {
            if (node.skin() != null) {
                return true;
            }
        }
        return false;
    }

    public int meshCount() {
        int count = 0;
        for (ModelNode node : allNodes) {
            if (node.mesh() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Which draw path this model's unskinned, non-morphed primitives should use — computed once, on
     * first call, and cached for the lifetime of this (shared, immutable) instance, exactly like the
     * "one decision per model, not per frame or per instance" HollowEngine itself makes (architecture
     * reference only — see {@link RenderPath}'s own doc for the two paths this chooses between). {@code
     * metadata().forceRenderPath()} — a {@code .smemeta} {@code "forceRenderPath"} override — always
     * wins when present, skipping the heuristic below entirely; see that field's own doc for why one is
     * needed at all: the heuristic is a static, per-model guess that can't know how many entities will
     * ever wear this model at once, and gets it backwards for exactly that "many instances of one
     * model" case (a crowd of NPCs) — confirmed in practice against a 46-tiny-primitive humanoid model
     * driven to single-digit fps by 100 concurrent BATCHING-path instances, resolved by pinning it to
     * {@code PIPELINE} instead.
     *
     * <p>Formula ported from HE, over the count/total-cubes/average-cubes of eligible primitives only
     * (skinned or morphed primitives never consult this — see {@code ModelRenderer#renderNode} — so
     * they're excluded from the average rather than skewing it): {@code BATCHING} if
     * {@code count >= 48}, or ({@code count >= 24} and {@code avgCubes <= 4}), or ({@code count >= 16}
     * and {@code totalCubes >= 128} and {@code avgCubes <= 8}); {@code PIPELINE} otherwise. The
     * specific numbers aren't explained by a comment in HE either — they're carried over as tuned
     * constants, not re-derived.
     */
    public RenderPath renderPath() {
        if (metadata.hasForceRenderPath()) {
            return metadata.forceRenderPath();
        }
        if (cachedRenderPath == null) {
            cachedRenderPath = computeRenderPath();
        }
        return cachedRenderPath;
    }

    private RenderPath computeRenderPath() {
        int count = 0;
        int totalCubes = 0;
        for (ModelNode node : allNodes) {
            if (node.mesh() == null || node.skin() != null) {
                continue;
            }
            for (Primitive primitive : node.mesh().primitives()) {
                if (!primitive.morphTargets().isEmpty()) {
                    continue;
                }
                count++;
                totalCubes += primitive.estimatedCubeCount();
            }
        }
        if (count == 0) {
            return RenderPath.PIPELINE;
        }
        double avgCubes = (double) totalCubes / count;
        boolean batching = count >= 48
                || (count >= 24 && avgCubes <= 4)
                || (count >= 16 && totalCubes >= 128 && avgCubes <= 8);
        return batching ? RenderPath.BATCHING : RenderPath.PIPELINE;
    }
}
