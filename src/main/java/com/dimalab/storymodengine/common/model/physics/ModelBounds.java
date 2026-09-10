package com.dimalab.storymodengine.common.model.physics;

import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.Primitive;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The axis-aligned extent of a model in its own space, measured in <b>blocks</b> (a model imported
 * through {@code ModelSpace} is already one unit per block). Computed from the <b>bind pose</b>: it's
 * a stable property of the asset, not something that should jitter every frame as an animation plays
 * — a hitbox that resized while a character walked would make collision feel broken rather than
 * responsive.
 *
 * <p>This is the one measurement both physical representations start from: an entity turns it into
 * {@link EntityDimensions}, and a block hands it to {@link ModelVoxelizer} to be fitted into the
 * unit cube.
 */
public record ModelBounds(Vector3f min, Vector3f max) {

    public static final ModelBounds EMPTY = new ModelBounds(new Vector3f(), new Vector3f());

    /** Walks the hierarchy accumulating each node's bind transform, and expands over every posed vertex. */
    public static ModelBounds of(ModelDefinition definition) {
        return accumulateAll(definition, node -> false);
    }

    /**
     * Same measurement as {@link #of}, but skips whichever node subtrees this model's own {@code
     * .smemeta} sidecar names in {@code hitboxExcludeNodes} (see {@code ModelMetadata}) — deliberately
     * not a fixed bone-name guess: a humanoid's raw bind-pose footprint is typically dominated by its
     * outstretched arms (the shipped {@code player_model.gltf} measures 1.13 across that way, spread
     * across nodes it itself calls {@code "LeftArm"}/{@code "RightArm"} — a different, model-specific
     * convention no fixed vocabulary could have guessed), far wider than the body a hitbox should
     * actually track. A model that declares nothing here is entirely unaffected — this equals {@link
     * #of} exactly, the same full-geometry measurement as before this existed.
     */
    public static ModelBounds footprint(ModelDefinition definition) {
        Set<String> excluded = Set.copyOf(definition.metadata().hitboxExcludeNodes());
        if (excluded.isEmpty()) {
            return of(definition);
        }
        return accumulateAll(definition, node -> excluded.contains(node.name()));
    }

    private static ModelBounds accumulateAll(ModelDefinition definition, Predicate<ModelNode> skip) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        boolean[] any = {false};

        for (ModelNode root : definition.roots()) {
            accumulate(root, new Matrix4f(), min, max, any, skip);
        }
        return any[0] ? new ModelBounds(min, max) : EMPTY;
    }

    /**
     * Each meshed node's own local extent — unlike {@link #of}, no parent (or even this node's own
     * bind) transform is accumulated here, because the caller applies a <i>live</i> transform instead
     * (see {@code client.model.ModelInstance#worldCullingBox}, which multiplies these corners by the
     * node's current-frame {@code RuntimeNode.globalMatrix()} rather than the node's fixed bind pose).
     * That's what makes the resulting world box pose-aware — an arm swung wide moves its own node's
     * box with it, rather than the whole model being tested against one box sized for the bind pose
     * and rotated only by entity yaw (the previous, coarser approach {@link #toWorldCullingBox} still
     * serves as a fallback for a model that opts out — see {@code ModelMetadata.disableInstanceCulling}).
     *
     * <p>A node with no mesh, or a mesh with no vertices, is simply absent from the result — nothing
     * for the caller to accumulate for it.
     */
    public static Map<Integer, ModelBounds> perNode(ModelDefinition definition) {
        Map<Integer, ModelBounds> result = new HashMap<>();
        for (ModelNode node : definition.allNodes()) {
            Mesh mesh = node.mesh();
            if (mesh == null) {
                continue;
            }
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            boolean any = false;
            Vector3f scratch = new Vector3f();
            for (Primitive primitive : mesh.primitives()) {
                float[] positions = primitive.vertices().positions();
                for (int i = 0; i + 2 < positions.length; i += 3) {
                    scratch.set(positions[i], positions[i + 1], positions[i + 2]);
                    min.min(scratch);
                    max.max(scratch);
                    any = true;
                }
            }
            if (any) {
                result.put(node.index(), new ModelBounds(min, max));
            }
        }
        return Map.copyOf(result);
    }

    private static void accumulate(ModelNode node, Matrix4f parentMatrix, Vector3f min, Vector3f max, boolean[] any, Predicate<ModelNode> skip) {
        if (skip.test(node)) {
            return;
        }
        Matrix4f local = new Matrix4f().translationRotateScale(node.bindTranslation(), node.bindRotation(), node.bindScale());
        Matrix4f global = parentMatrix.mul(local, new Matrix4f());

        Mesh mesh = node.mesh();
        if (mesh != null) {
            Vector3f scratch = new Vector3f();
            for (Primitive primitive : mesh.primitives()) {
                float[] positions = primitive.vertices().positions();
                for (int i = 0; i + 2 < positions.length; i += 3) {
                    scratch.set(positions[i], positions[i + 1], positions[i + 2]);
                    global.transformPosition(scratch);
                    min.min(scratch);
                    max.max(scratch);
                    any[0] = true;
                }
            }
        }
        for (ModelNode child : node.children()) {
            accumulate(child, global, min, max, any, skip);
        }
    }

    public boolean isEmpty() {
        return equals(EMPTY) || max.x < min.x || max.y < min.y || max.z < min.z;
    }

    public float sizeX() {
        return max.x - min.x;
    }

    public float sizeY() {
        return max.y - min.y;
    }

    public float sizeZ() {
        return max.z - min.z;
    }

    public Vector3f center(Vector3f dest) {
        return min.add(max, dest).mul(0.5f);
    }

    /**
     * Vanilla entity collision is a single upright box described by one width and one height — there
     * is no multi-box or mesh option for entities in 1.20.1, so the horizontal extent collapses to
     * the larger of X/Z (a box that contains the model rather than clipping it).
     */
    public EntityDimensions toEntityDimensions() {
        return toEntityDimensions(0f);
    }

    /**
     * The same box, sized for a model drawn rotated by {@code yawDegrees}.
     *
     * <p>An entity's bounding box is always world-axis-aligned — Minecraft has no rotated hitbox, and
     * nothing here can change that. What it can do is size the box so it still <i>contains</i> the
     * rotated model: a footprint of {@code x × z} turned by θ spans {@code x·|cos θ| + z·|sin θ|}
     * across one axis and {@code x·|sin θ| + z·|cos θ|} across the other. Taking the larger keeps the
     * single square footprint honest at any angle.
     *
     * <p>Without this the box stays sized for the unrotated model while the mesh is drawn turned, so
     * the corners of the model visibly hang outside its own hitbox — exactly what switching on
     * {@code F3+B} showed.
     */
    public EntityDimensions toEntityDimensions(float yawDegrees) {
        if (isEmpty()) {
            return EntityDimensions.fixed(0.5f, 0.5f);
        }
        double radians = Math.toRadians(yawDegrees);
        float cos = (float) Math.abs(Math.cos(radians));
        float sin = (float) Math.abs(Math.sin(radians));
        float alongX = sizeX() * cos + sizeZ() * sin;
        float alongZ = sizeX() * sin + sizeZ() * cos;
        return EntityDimensions.fixed(Math.max(alongX, alongZ), sizeY());
    }

    /** This extent as a world-space {@link AABB} centered on {@code (x, z)} and resting on {@code y}. */
    public AABB toWorldBox(double x, double y, double z) {
        float halfX = sizeX() * 0.5f;
        float halfZ = sizeZ() * 0.5f;
        return new AABB(x - halfX, y, z - halfZ, x + halfX, y + sizeY(), z + halfZ);
    }

    /**
     * This bind-pose box's 8 corners, transformed into world space by {@code (x, y, z)} and a yaw
     * rotation, folded into a min/max {@link AABB} — for a per-frame frustum-culling test, where a
     * fixed hitbox-derived box (what vanilla's own culling uses) can't account for a pose — an
     * attack swing, say — reaching past the model's resting extent. Cheap on purpose: 8 points
     * transformed, not the all-vertex scan {@link #of} does once at load to measure the box itself.
     *
     * <p>Which way {@code yawDegrees} turns the box doesn't need to be exactly right the way it does
     * for actual rendering — the corners of an axis-aligned box mapped to their mirror image still
     * envelope the same volume to within the box's own asymmetry, and the caller inflates the result
     * afterward regardless. Getting this box <i>generously</i> right matters; getting its rotation
     * sign convention exactly right does not.
     */
    public AABB toWorldCullingBox(double x, double y, double z, float yawDegrees) {
        if (isEmpty()) {
            return new AABB(x, y, z, x, y, z);
        }
        Matrix4f transform = new Matrix4f()
                .translate((float) x, (float) y, (float) z)
                .rotateY((float) Math.toRadians(-yawDegrees));

        Vector3f corner = new Vector3f();
        Vector3f worldMin = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f worldMax = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < 8; i++) {
            corner.set(
                    (i & 1) == 0 ? min.x : max.x,
                    (i & 2) == 0 ? min.y : max.y,
                    (i & 4) == 0 ? min.z : max.z);
            transform.transformPosition(corner);
            worldMin.min(corner);
            worldMax.max(corner);
        }
        return new AABB(worldMin.x, worldMin.y, worldMin.z, worldMax.x, worldMax.y, worldMax.z);
    }
}
