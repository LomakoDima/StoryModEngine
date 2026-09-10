package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.MaterialData;
import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelMetadata;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.ModelSpace;
import com.dimalab.storymodengine.common.model.Skin;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns one {@code .gltf}/{@code .glb} file into a {@link ModelDefinition}.
 *
 * <p><b>The whole node graph is preserved</b>, not flattened. glTF's node hierarchy is the model's
 * actual structure — where each mesh sits, what a bone's rest pose is, and the parent chain skinning
 * walks — so the importer builds a real {@link ModelNode} tree with parent links and per-node TRS.
 * (An earlier version picked the first node that happened to own a mesh and dropped everything else,
 * which silently collapsed every multi-part model to the origin.)
 *
 * <p>Order matters and is fixed: materials, then skins (which only need raw joint node indices), then
 * the node tree (nodes reference meshes and skins), then animations (which need each target node's
 * bind pose to store deltas against — see {@link GltfAnimationImporter}), then the {@link ModelSpace}
 * facing correction wrapped around the finished roots.
 */
public final class GltfModelParser {

    private static final Gson GSON = new Gson();

    private GltfModelParser() {
    }

    /** Parses with no sidecar metadata — exporter-sniffed facing, no rig. */
    public static ModelDefinition parse(ResourceLocation id, byte[] bytes, ResourceManager resourceManager) {
        return parse(id, bytes, resourceManager, ModelMetadata.EMPTY);
    }

    public static ModelDefinition parse(ResourceLocation id, byte[] bytes, ResourceManager resourceManager, ModelMetadata metadata) {
        String json;
        byte[] binChunk = null;
        if (GlbContainer.isGlb(bytes)) {
            GlbContainer container = GlbContainer.parse(id, bytes);
            json = container.json();
            binChunk = container.binChunk();
        } else {
            json = new String(bytes, StandardCharsets.UTF_8);
        }

        GltfDocument document;
        try {
            document = GSON.fromJson(json, GltfDocument.class);
        } catch (Exception e) {
            throw new ModelFormatException(id, "invalid glTF JSON", e);
        }
        if (document == null) {
            throw new ModelFormatException(id, "empty glTF document");
        }

        GltfBufferResolver buffers = new GltfBufferResolver(document, binChunk, resourceManager, id);
        GltfAccessorReader accessorReader = new GltfAccessorReader(document, buffers, id);

        List<MaterialData> materials = new GltfMaterialImporter(document, buffers, id, metadata.skinMaterialIndex()).importAll();
        List<Skin> skins = importSkins(document, accessorReader, id);
        GltfMeshImporter meshImporter = new GltfMeshImporter(document, accessorReader, materials, id);

        List<ModelNode> roots = buildNodeTree(document, meshImporter, skins, id);
        Map<Integer, ModelNode> nodesByIndex = new LinkedHashMap<>();
        for (ModelNode root : roots) {
            indexNodes(root, nodesByIndex);
        }

        List<AnimationClip> animations = new GltfAnimationImporter(document, accessorReader, nodesByIndex, id).importAll();

        String generator = document.asset != null ? document.asset.generator : null;
        List<ModelNode> placed = ModelSpace.place(roots, ModelSpace.needsFacingCorrection(generator, metadata.facing()), 1f);

        ModelDefinition definition = new ModelDefinition(id, placed, animations, metadata);
        if (definition.meshCount() == 0) {
            EngineLog.channel("Model").warn("{}: document has no drawable meshes", id);
        }
        return definition;
    }

    private static List<Skin> importSkins(GltfDocument document, GltfAccessorReader accessors, ResourceLocation id) {
        List<Skin> skins = new ArrayList<>();
        if (document.skins == null) {
            return skins;
        }
        for (GltfDocument.GltfSkin raw : document.skins) {
            // Both structural: glTF requires "joints" on every skin, and nothing about the format
            // guarantees a separately-declared inverseBindMatrices accessor's own count agrees with
            // it — left unchecked, the first throws an unhelpful NPE right here, and the second
            // throws a confusing ArrayIndexOutOfBoundsException much later inside CpuSkinner.
            if (raw.joints == null || raw.joints.isEmpty()) {
                throw new ModelFormatException(id, "skin has no joints array");
            }
            int[] jointNodes = new int[raw.joints.size()];
            for (int i = 0; i < jointNodes.length; i++) {
                jointNodes[i] = raw.joints.get(i);
            }
            Matrix4f[] inverseBind;
            if (raw.inverseBindMatrices != null) {
                inverseBind = accessors.readMatrices(raw.inverseBindMatrices);
                if (inverseBind.length != jointNodes.length) {
                    throw new ModelFormatException(id, "skin declares " + jointNodes.length
                            + " joints but " + inverseBind.length + " inverse bind matrices");
                }
            } else {
                // Spec-legal: absent inverse bind matrices mean identity for every joint.
                inverseBind = new Matrix4f[jointNodes.length];
                for (int i = 0; i < inverseBind.length; i++) {
                    inverseBind[i] = new Matrix4f();
                }
            }
            skins.add(new Skin(jointNodes, inverseBind));
        }
        return skins;
    }

    private static List<ModelNode> buildNodeTree(GltfDocument document, GltfMeshImporter meshImporter, List<Skin> skins, ResourceLocation id) {
        List<ModelNode> roots = new ArrayList<>();
        if (document.nodes == null || document.nodes.isEmpty()) {
            return roots;
        }

        List<Integer> rootIndices = resolveRootIndices(document);
        Set<Integer> visiting = new HashSet<>();
        for (int rootIndex : rootIndices) {
            ModelNode node = buildNode(document, rootIndex, meshImporter, skins, visiting, id);
            if (node != null) {
                roots.add(node);
            }
        }
        return roots;
    }

    /** The declared scene's roots when there is one; otherwise every node nobody lists as a child — the same thing a scene would have named. */
    private static List<Integer> resolveRootIndices(GltfDocument document) {
        if (document.scenes != null && !document.scenes.isEmpty()) {
            int sceneIndex = document.scene != null && document.scene < document.scenes.size() ? document.scene : 0;
            List<Integer> declared = document.scenes.get(sceneIndex).nodes;
            if (declared != null && !declared.isEmpty()) {
                return declared;
            }
        }

        Set<Integer> children = new HashSet<>();
        for (GltfDocument.GltfNode node : document.nodes) {
            if (node.children != null) {
                children.addAll(node.children);
            }
        }
        List<Integer> roots = new ArrayList<>();
        for (int i = 0; i < document.nodes.size(); i++) {
            if (!children.contains(i)) {
                roots.add(i);
            }
        }
        return roots;
    }

    private static ModelNode buildNode(GltfDocument document, int nodeIndex, GltfMeshImporter meshImporter, List<Skin> skins, Set<Integer> visiting, ResourceLocation id) {
        // glTF requires the node graph to be acyclic; a malformed file that isn't would otherwise
        // recurse until the stack overflows, so the cycle is cut and reported instead.
        if (!visiting.add(nodeIndex)) {
            EngineLog.channel("Model").warn("{}: node {} is part of a cycle — branch dropped", id, nodeIndex);
            return null;
        }
        try {
            GltfDocument.GltfNode raw = document.nodes.get(nodeIndex);

            // Skin resolved first — GeometryUtils.sanitizeSkinning (run inside importMesh, for a
            // skinned mesh) needs the skin's own joint count, so the mesh import below can't run
            // before this.
            Skin skin = raw.skin != null && raw.skin < skins.size() ? skins.get(raw.skin) : null;
            Mesh mesh = raw.mesh != null ? meshImporter.importMesh(raw.mesh, skin) : null;

            Vector3f translation = new Vector3f();
            Quaternionf rotation = new Quaternionf();
            Vector3f scale = new Vector3f(1f, 1f, 1f);
            readTransform(raw, translation, rotation, scale);

            String name = raw.name != null ? raw.name : ("node_" + nodeIndex);
            float[] morphWeights = resolveMorphWeights(document, raw);
            ModelNode node = new ModelNode(nodeIndex, name, translation, rotation, scale, mesh, skin, morphWeights);

            if (raw.children != null) {
                for (int childIndex : raw.children) {
                    ModelNode child = buildNode(document, childIndex, meshImporter, skins, visiting, id);
                    if (child != null) {
                        node.addChild(child);
                    }
                }
            }
            return node;
        } finally {
            visiting.remove(nodeIndex);
        }
    }

    /**
     * A glTF node states its transform either as a 4×4 {@code matrix} or as separate {@code
     * translation}/{@code rotation}/{@code scale} — never both, per spec. The matrix form is stored
     * column-major, which is exactly what {@code Matrix4f.set(float[])} expects, so it needs no
     * transpose; it's then decomposed back to TRS because everything downstream (animation deltas,
     * pose blending) is expressed in TRS terms. An earlier version ignored {@code matrix} entirely
     * and treated such nodes as identity.
     */
    private static void readTransform(GltfDocument.GltfNode raw, Vector3f translation, Quaternionf rotation, Vector3f scale) {
        if (raw.matrix != null && raw.matrix.length == 16) {
            Matrix4f matrix = new Matrix4f().set(raw.matrix);
            matrix.getTranslation(translation);
            matrix.getScale(scale);
            matrix.getNormalizedRotation(rotation);
            return;
        }
        if (raw.translation != null) {
            translation.set(raw.translation[0], raw.translation[1], raw.translation[2]);
        }
        if (raw.rotation != null) {
            rotation.set(raw.rotation[0], raw.rotation[1], raw.rotation[2], raw.rotation[3]);
        }
        if (raw.scale != null) {
            scale.set(raw.scale[0], raw.scale[1], raw.scale[2]);
        }
    }

    /** A node's own {@code weights} override its mesh's default, per spec; a node with no mesh (or no weights declared anywhere) has none. */
    private static float[] resolveMorphWeights(GltfDocument document, GltfDocument.GltfNode raw) {
        if (raw.weights != null) {
            return raw.weights;
        }
        if (raw.mesh != null) {
            GltfDocument.GltfMesh rawMesh = document.meshes.get(raw.mesh);
            if (rawMesh.weights != null) {
                return rawMesh.weights;
            }
        }
        return new float[0];
    }

    private static void indexNodes(ModelNode node, Map<Integer, ModelNode> out) {
        out.put(node.index(), node);
        for (ModelNode child : node.children()) {
            indexNodes(child, out);
        }
    }
}
