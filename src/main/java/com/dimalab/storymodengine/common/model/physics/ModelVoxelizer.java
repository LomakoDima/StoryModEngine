package com.dimalab.storymodengine.common.model.physics;

import com.dimalab.storymodengine.common.model.Mesh;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.voxel.ShapeDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Turns a model's triangles into the boxes a block's collision is made of, then hands them to the
 * existing {@code voxel} package — the result is an ordinary {@link ShapeDefinition}, so it inherits
 * that package's union, caching and {@code rotated(facing)} support for free rather than growing a
 * parallel shape system.
 *
 * <p><b>Why voxelize instead of using each mesh part's bounding box.</b> Per-part boxes are cheaper,
 * but they're only a good approximation when a model is authored as separate blocky parts; a single
 * organic mesh collapses to one box the size of the whole model, which collides as a crate. Marking
 * the cells the geometry actually passes through gives collision that follows the silhouette, and at
 * Minecraft's own 16-per-block grid the blocky result is exactly the feel of a vanilla block.
 *
 * <p>Three stages: rasterize triangles into a grid ({@link TriangleBoxOverlap}, exact), optionally
 * flood-fill the outside so enclosed space becomes solid rather than hollow, and greedily merge
 * runs of cells into as few boxes as possible — a naive box-per-cell shape would hand {@code
 * Shapes.or} thousands of boxes and make every collision query slow.
 */
public final class ModelVoxelizer {

    /** Minecraft's own model grid: 16 subdivisions per block. */
    public static final int DEFAULT_RESOLUTION = 16;

    private ModelVoxelizer() {
    }

    /**
     * Collision shape for a model placed as a block, fitted into the unit cube: scaled uniformly (so
     * the model isn't distorted), centered horizontally, resting on the floor. A model taller than it
     * is wide therefore ends up narrower than a full block, which is what "this object occupies one
     * block space" should look like. Multi-block models are out of scope — see MODEL_SYSTEM_DESIGN.md.
     */
    public static ShapeDefinition forBlock(ModelDefinition definition, int resolution, boolean solid) {
        ModelBounds bounds = ModelBounds.of(definition);
        if (bounds.isEmpty()) {
            return ShapeDefinition.EMPTY;
        }

        float largest = Math.max(bounds.sizeX(), Math.max(bounds.sizeY(), bounds.sizeZ()));
        if (largest <= 0f) {
            return ShapeDefinition.EMPTY;
        }
        float scale = 1f / largest;

        // Model space -> unit cube: scale uniformly, center X/Z, drop Y to the floor.
        Vector3f center = bounds.center(new Vector3f());
        Matrix4f fit = new Matrix4f()
                .translate(0.5f, 0f, 0.5f)
                .scale(scale)
                .translate(-center.x, -bounds.min().y, -center.z);

        boolean[][][] grid = rasterize(definition, fit, resolution);
        if (solid) {
            fillEnclosed(grid, resolution);
        }
        return toShape(grid, resolution);
    }

    public static ShapeDefinition forBlock(ModelDefinition definition) {
        return forBlock(definition, DEFAULT_RESOLUTION, true);
    }

    private static boolean[][][] rasterize(ModelDefinition definition, Matrix4f fit, int resolution) {
        boolean[][][] grid = new boolean[resolution][resolution][resolution];
        float cell = 1f / resolution;
        float half = cell * 0.5f;
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f cellCenter = new Vector3f();

        for (ModelNode root : definition.roots()) {
            rasterizeNode(root, new Matrix4f(), fit, grid, resolution, cell, half, a, b, c, cellCenter);
        }
        return grid;
    }

    private static void rasterizeNode(ModelNode node, Matrix4f parentMatrix, Matrix4f fit,
                                      boolean[][][] grid, int resolution, float cell, float half,
                                      Vector3f a, Vector3f b, Vector3f c, Vector3f cellCenter) {
        Matrix4f local = new Matrix4f().translationRotateScale(node.bindTranslation(), node.bindRotation(), node.bindScale());
        Matrix4f global = parentMatrix.mul(local, new Matrix4f());
        Matrix4f toGrid = fit.mul(global, new Matrix4f());

        Mesh mesh = node.mesh();
        if (mesh != null) {
            for (Primitive primitive : mesh.primitives()) {
                float[] positions = primitive.vertices().positions();
                int[] indices = primitive.indices();
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    load(a, positions, indices[i], toGrid);
                    load(b, positions, indices[i + 1], toGrid);
                    load(c, positions, indices[i + 2], toGrid);
                    markTriangle(a, b, c, grid, resolution, cell, half, cellCenter);
                }
            }
        }
        for (ModelNode child : node.children()) {
            rasterizeNode(child, global, fit, grid, resolution, cell, half, a, b, c, cellCenter);
        }
    }

    private static void load(Vector3f dest, float[] positions, int index, Matrix4f matrix) {
        dest.set(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
        matrix.transformPosition(dest);
    }

    private static void markTriangle(Vector3f a, Vector3f b, Vector3f c, boolean[][][] grid,
                                     int resolution, float cell, float half, Vector3f cellCenter) {
        int minX = clampCell((int) Math.floor(Math.min(a.x, Math.min(b.x, c.x)) / cell), resolution);
        int maxX = clampCell((int) Math.floor(Math.max(a.x, Math.max(b.x, c.x)) / cell), resolution);
        int minY = clampCell((int) Math.floor(Math.min(a.y, Math.min(b.y, c.y)) / cell), resolution);
        int maxY = clampCell((int) Math.floor(Math.max(a.y, Math.max(b.y, c.y)) / cell), resolution);
        int minZ = clampCell((int) Math.floor(Math.min(a.z, Math.min(b.z, c.z)) / cell), resolution);
        int maxZ = clampCell((int) Math.floor(Math.max(a.z, Math.max(b.z, c.z)) / cell), resolution);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (grid[x][y][z]) {
                        continue;
                    }
                    cellCenter.set((x + 0.5f) * cell, (y + 0.5f) * cell, (z + 0.5f) * cell);
                    if (TriangleBoxOverlap.test(a, b, c, cellCenter, half, half, half)) {
                        grid[x][y][z] = true;
                    }
                }
            }
        }
    }

    private static int clampCell(int value, int resolution) {
        return Math.max(0, Math.min(resolution - 1, value));
    }

    /**
     * Flood-fills empty space reachable from outside the grid; whatever it can't reach is interior
     * and becomes solid. Without this a closed mesh collides as a shell — a player could clip inside
     * and stand in the middle of the object.
     */
    private static void fillEnclosed(boolean[][][] grid, int resolution) {
        boolean[][][] outside = new boolean[resolution][resolution][resolution];
        Deque<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    boolean onBorder = x == 0 || y == 0 || z == 0
                            || x == resolution - 1 || y == resolution - 1 || z == resolution - 1;
                    if (onBorder && !grid[x][y][z]) {
                        outside[x][y][z] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }

        int[][] neighbours = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int[] offset : neighbours) {
                int nx = current[0] + offset[0];
                int ny = current[1] + offset[1];
                int nz = current[2] + offset[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= resolution || ny >= resolution || nz >= resolution) {
                    continue;
                }
                if (outside[nx][ny][nz] || grid[nx][ny][nz]) {
                    continue;
                }
                outside[nx][ny][nz] = true;
                queue.add(new int[]{nx, ny, nz});
            }
        }

        for (int x = 0; x < resolution; x++) {
            for (int y = 0; y < resolution; y++) {
                for (int z = 0; z < resolution; z++) {
                    if (!outside[x][y][z]) {
                        grid[x][y][z] = true;
                    }
                }
            }
        }
    }

    /**
     * Greedy meshing, one Y layer at a time: extend a run along X as far as it goes, then extend that
     * whole run along Z while every cell of it is still free. Cuts a 16³ shape from potentially
     * thousands of boxes to a few dozen, which is the difference between a usable collision shape and
     * one that stalls every {@code Shapes.or} query against it.
     */
    private static ShapeDefinition toShape(boolean[][][] grid, int resolution) {
        // ShapeDefinition.Builder speaks Minecraft's own model units (0..16) and does the /16 itself,
        // so the grid index is scaled into that space rather than into [0,1] — one conversion, in the
        // place the voxel package already owns it.
        double unit = 16.0 / resolution;
        ShapeDefinition.Builder builder = ShapeDefinition.builder();

        for (int y = 0; y < resolution; y++) {
            boolean[][] used = new boolean[resolution][resolution];
            for (int x = 0; x < resolution; x++) {
                for (int z = 0; z < resolution; z++) {
                    if (!grid[x][y][z] || used[x][z]) {
                        continue;
                    }
                    int width = 1;
                    while (x + width < resolution && grid[x + width][y][z] && !used[x + width][z]) {
                        width++;
                    }
                    int depth = 1;
                    while (z + depth < resolution && rowFree(grid, used, x, y, z + depth, width)) {
                        depth++;
                    }
                    for (int dx = 0; dx < width; dx++) {
                        for (int dz = 0; dz < depth; dz++) {
                            used[x + dx][z + dz] = true;
                        }
                    }
                    builder.box(
                            x * unit, y * unit, z * unit,
                            (x + width) * unit, (y + 1) * unit, (z + depth) * unit);
                }
            }
        }
        return builder.build();
    }

    private static boolean rowFree(boolean[][][] grid, boolean[][] used, int x, int y, int z, int width) {
        for (int dx = 0; dx < width; dx++) {
            if (!grid[x + dx][y][z] || used[x + dx][z]) {
                return false;
            }
        }
        return true;
    }
}
