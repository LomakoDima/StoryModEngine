package com.dimalab.storymodengine.common.model.gltf;

import java.util.List;
import java.util.Map;

/**
 * Raw Gson-mapped glTF 2.0 JSON schema — only the fields this importer actually reads (positions/
 * normals/tangents/uv0/uv1/joints/weights/indices, morph targets, materials' full PBR metallic-
 * roughness set (base color, normal, occlusion, emissive, metallic/roughness, alpha mode), skins,
 * animations, and the node graph needed to resolve joint hierarchy/bind transforms). Every other
 * glTF field (cameras, lights, extensions, extras, ...) is simply never declared here, and Gson
 * silently ignores whatever it finds no matching field for — exactly the subset behavior this
 * importer wants, not a bug or an oversight.
 *
 * <p>Plain mutable fields, not records: Gson's reflective adapter constructs and fills these via
 * {@code sun.misc.Unsafe} regardless of whether the target is a record, and a plain class sidesteps
 * any Gson-version uncertainty around record support (this project's bundled Gson version, a
 * transitive Minecraft/Forge dependency, was not independently version-pinned for this feature).
 * Field names match the glTF spec's own JSON keys 1:1 (including {@code SCREAMING_CASE} attribute
 * names like {@code POSITION}/{@code JOINTS_0}), so no {@code @SerializedName} is needed anywhere.
 * One file, several small nested classes, deliberately — splitting a raw format mirror this size
 * into fifteen one-class files would be ceremony, not clarity.
 */
public final class GltfDocument {
    public GltfAsset asset;
    public List<GltfBuffer> buffers;
    public List<GltfBufferView> bufferViews;
    public List<GltfAccessor> accessors;
    public List<GltfMesh> meshes;
    public List<GltfMaterial> materials;
    public List<GltfImage> images;
    public List<GltfTexture> textures;
    public List<GltfSkin> skins;
    public List<GltfAnimation> animations;
    public List<GltfNode> nodes;
    public List<GltfScene> scenes;
    public Integer scene;

    /**
     * {@code generator} names the tool that wrote the file. Load-bearing, not metadata: it's how
     * {@code ModelSpace} tells a Blockbench export (already in Minecraft's orientation) from every
     * other exporter (glTF's own +Z-forward convention) without asking the mod author to configure it.
     */
    public static final class GltfAsset {
        public String version;
        public String generator;
    }

    public static final class GltfBuffer {
        public String uri;
        public int byteLength;
    }

    public static final class GltfBufferView {
        public int buffer;
        public Integer byteOffset;
        public int byteLength;
        public Integer byteStride;
    }

    public static final class GltfAccessor {
        public Integer bufferView;
        public Integer byteOffset;
        public int componentType;
        public boolean normalized;
        public int count;
        public String type;
        /**
         * Sparse storage: most elements come from {@code bufferView} (or are zero when it's absent),
         * and only the listed indices are overridden. Exporters use it for meshes where a handful of
         * values differ from a shared base. Reading the base and ignoring the overrides produces
         * silently wrong geometry, which is why this is parsed rather than skipped.
         */
        public GltfSparse sparse;
    }

    public static final class GltfSparse {
        public int count;
        public GltfSparseIndices indices;
        public GltfSparseValues values;
    }

    public static final class GltfSparseIndices {
        public int bufferView;
        public Integer byteOffset;
        public int componentType;
    }

    public static final class GltfSparseValues {
        public int bufferView;
        public Integer byteOffset;
    }

    public static final class GltfMesh {
        public String name;
        public List<GltfPrimitive> primitives;
        /** Default morph target weights for every primitive of this mesh, overridden per-node by {@link GltfNode#weights}. */
        public float[] weights;
    }

    public static final class GltfPrimitive {
        public GltfAttributes attributes;
        public Integer indices;
        public Integer material;
        /** glTF primitive mode; 4 = TRIANGLES, the default and the only one this importer draws. Checked rather than assumed — see {@code GltfMeshImporter}. */
        public Integer mode;
        /** Morph targets: each entry maps an attribute name ({@code POSITION}/{@code NORMAL}) to the accessor holding that attribute's per-vertex delta from the base mesh. */
        public List<Map<String, Integer>> targets;
    }

    public static final class GltfAttributes {
        public Integer POSITION;
        public Integer NORMAL;
        public Integer TANGENT;
        public Integer TEXCOORD_0;
        public Integer TEXCOORD_1;
        public Integer JOINTS_0;
        public Integer WEIGHTS_0;
    }

    public static final class GltfMaterial {
        public String name;
        public GltfPbrMetallicRoughness pbrMetallicRoughness;
        public GltfTextureRef normalTexture;
        public GltfTextureRef occlusionTexture;
        public GltfTextureRef emissiveTexture;
        public float[] emissiveFactor;
        /** {@code "OPAQUE"} / {@code "MASK"} / {@code "BLEND"} per spec; absent means {@code OPAQUE}, the spec default. */
        public String alphaMode;
        /** Meaningful only under {@code MASK}; spec default 0.5 when absent — see {@link GltfMaterialImporter} for why this is boxed, not primitive. */
        public Float alphaCutoff;
        public boolean doubleSided;
    }

    public static final class GltfPbrMetallicRoughness {
        public float[] baseColorFactor;
        public GltfTextureRef baseColorTexture;
        /** Spec default 1.0 when absent — boxed, not primitive; see {@link GltfMaterialImporter}. */
        public Float metallicFactor;
        /** Spec default 1.0 when absent — boxed, not primitive; see {@link GltfMaterialImporter}. */
        public Float roughnessFactor;
        public GltfTextureRef metallicRoughnessTexture;
    }

    /**
     * A texture reference plus whichever of its two rarely-used spec fields apply to that reference
     * ({@code scale} only means anything on {@code normalTexture}, {@code strength} only on {@code
     * occlusionTexture} — both meaningless, and simply left {@code null}, everywhere else this type is
     * reused). Boxed, not primitive — see {@link GltfMaterialImporter}'s own doc for why a nonzero spec
     * default on a primitive field here would silently read back as zero from Gson.
     */
    public static final class GltfTextureRef {
        public int index;
        /** Which UV set this reference samples — {@code TEXCOORD_0}/{@code TEXCOORD_1}. Defaults to 0 per spec when absent. */
        public Integer texCoord;
        /** {@code normalTexture} only; spec default 1.0 when absent. */
        public Float scale;
        /** {@code occlusionTexture} only; spec default 1.0 when absent. */
        public Float strength;
    }

    public static final class GltfImage {
        public String uri;
        public Integer bufferView;
        public String mimeType;
    }

    public static final class GltfTexture {
        public Integer source;
    }

    public static final class GltfSkin {
        public Integer inverseBindMatrices;
        public List<Integer> joints;
        public Integer skeleton;
    }

    public static final class GltfAnimation {
        public String name;
        public List<GltfAnimationChannel> channels;
        public List<GltfAnimationSampler> samplers;
    }

    public static final class GltfAnimationChannel {
        public int sampler;
        public GltfAnimationTarget target;
    }

    public static final class GltfAnimationTarget {
        public Integer node;
        public String path;
    }

    public static final class GltfAnimationSampler {
        public int input;
        public int output;
        public String interpolation;
    }

    public static final class GltfNode {
        public String name;
        public List<Integer> children;
        public Integer mesh;
        public Integer skin;
        public float[] translation;
        public float[] rotation;
        public float[] scale;
        public float[] matrix;
        /** Overrides the mesh's default morph target weights for this particular instance of it. */
        public float[] weights;
    }

    public static final class GltfScene {
        public List<Integer> nodes;
    }
}
