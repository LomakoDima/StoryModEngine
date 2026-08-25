package com.dimalab.storymodengine.common.multiblock;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;

import java.util.List;
import java.util.Map;

/**
 * The precomputed, immutable 3D grid a {@link Multiblock} checks against — one {@link
 * PatternMatcher} per cell, built once by {@link #compile} and never touched again. Dimensions
 * follow the convention documented on {@link Multiblock}: {@link #height} is the number of
 * {@code .layer(...)} calls (Y), {@link #depth} is the number of rows per layer (Z), {@link
 * #width} is the number of characters per row (X).
 *
 * <p>Deliberately holds only what {@link MultiblockDetector} needs to check a position — no
 * retained char grid, no back-reference to which {@code where(char, ...)} symbol produced which
 * matcher. Nothing in this system's current surface needs that mapping; a future consumer that
 * does can already reconstruct it from its own {@code where()} map plus these three dimensions.
 */
public final class MultiblockPattern {

    private final int width;
    private final int height;
    private final int depth;
    private final PatternMatcher[][][] grid; // [y][z][x]

    private MultiblockPattern(int width, int height, int depth, PatternMatcher[][][] grid) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.grid = grid;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    PatternMatcher matcherAt(int x, int y, int z) {
        return grid[y][z][x];
    }

    /**
     * Validates {@code layers} (one {@code String[]} per Y-level, as accumulated by {@link
     * Multiblock.Builder#layer}) against {@code matchers} (as accumulated by {@link
     * Multiblock.Builder#where}) and builds the precomputed grid, or throws {@link
     * IllegalStateException} with a message naming exactly what's wrong and where — never an
     * {@code ArrayIndexOutOfBoundsException} or other incidental failure.
     */
    static MultiblockPattern compile(String name, List<String[]> layers, Map<Character, PatternMatcher> matchers) {
        if (layers.isEmpty()) {
            throw new IllegalStateException(
                    "Multiblock '" + name + "': pattern is empty — call layer(...) at least once before build()");
        }

        int height = layers.size();
        String[] firstLayer = layers.get(0);
        int depth = firstLayer.length;
        if (depth == 0) {
            throw new IllegalStateException("Multiblock '" + name + "': layer 0 has no rows");
        }
        int width = firstLayer[0].length();
        if (width == 0) {
            throw new IllegalStateException("Multiblock '" + name + "': layer 0 row 0 is empty");
        }

        PatternMatcher[][][] grid = new PatternMatcher[height][depth][width];
        for (int y = 0; y < height; y++) {
            String[] rows = layers.get(y);
            if (rows.length != depth) {
                throw new IllegalStateException("Multiblock '" + name + "': layer " + y + " has "
                        + rows.length + " row(s), expected " + depth);
            }
            for (int z = 0; z < depth; z++) {
                String row = rows[z];
                if (row.length() != width) {
                    throw new IllegalStateException("Multiblock '" + name + "': layer " + y + " row " + z
                            + " has length " + row.length() + ", expected " + width);
                }
                for (int x = 0; x < width; x++) {
                    char symbol = row.charAt(x);
                    PatternMatcher matcher = matchers.get(symbol);
                    if (matcher == null) {
                        throw new IllegalStateException("Multiblock '" + name + "': unknown pattern symbol '"
                                + symbol + "' at layer " + y + ", row " + z + ", column " + x
                                + " — no matcher registered via where('" + symbol + "', ...)");
                    }
                    grid[y][z][x] = matcher;
                }
            }
        }
        return new MultiblockPattern(width, height, depth, grid);
    }
}
