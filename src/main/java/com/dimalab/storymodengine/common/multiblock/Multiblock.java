package com.dimalab.storymodengine.common.multiblock;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, named description of a 3D block pattern — nothing more. A {@code Multiblock}
 * only describes and checks a spatial pattern; it never knows about machines, controllers,
 * recipes, energy, GUIs, or any {@code BlockEntity} — what happens after a successful {@link
 * #find} is entirely up to the caller. See {@code ARCHITECTURE.md}'s "Multiblock System"
 * section for the full rationale.
 *
 * <h2>Coordinate convention</h2>
 * A pattern is built from one {@link Builder#layer} call per Y-level (layer 0 = bottom,
 * increasing upward), each call taking one {@code String} per row. Within a layer, a row's
 * index in that array is Z; within a row, a character's index is X. {@link PatternRotation#NORTH}
 * is the identity — a pattern is authored exactly as it will appear in the world under
 * {@code NORTH} (pattern-local +X → world +X/EAST, pattern-local +Z → world +Z/SOUTH). {@code
 * origin} in {@link #find} is the world position pattern-local {@code (0, 0, 0)} maps to: the
 * bottom layer's northernmost, westernmost cell, before rotation.
 *
 * <pre>{@code
 * import static com.dimalab.storymodengine.common.multiblock.PatternMatchers.*;
 *
 * public static final Multiblock ALTAR = Multiblock.define("altar")
 *         .layer("ODO", "DAD", "ODO")
 *         .layer("OAO", "AAA", "OAO")
 *         .layer("ODO", "DOD", "ODO")
 *         .where('O', block(Blocks.OBSIDIAN))
 *         .where('D', block(Blocks.DIAMOND_BLOCK))
 *         .where('A', air())
 *         .build();
 *
 * Optional<MultiblockMatch> match = ALTAR.find(level, pos);
 * }</pre>
 *
 * <p>One {@code .layer(...)} call per Y-level (rather than one flat, all-layers-at-once
 * {@code .pattern(...)} call) is deliberate: a single flattened list of rows has no way to
 * recover where one layer ends and the next begins without an extra dimension argument or a
 * sentinel value. Vanilla Minecraft's own {@code BlockPatternBuilder} resolves the identical
 * ambiguity the same way, with repeated {@code .aisle(...)} calls — this mirrors that,
 * renamed to this engine's own "layer" vocabulary.
 */
public final class Multiblock {

    private final String name;
    private final MultiblockPattern pattern;

    private Multiblock(String name, MultiblockPattern pattern) {
        this.name = name;
        this.pattern = pattern;
    }

    public static Builder define(String name) {
        return new Builder(Objects.requireNonNull(name, "name"));
    }

    public String name() {
        return name;
    }

    public MultiblockPattern pattern() {
        return pattern;
    }

    /** Tries every {@link PatternRotation} in turn — see {@link MultiblockDetector#find(Multiblock, BlockGetter, BlockPos)}. */
    public Optional<MultiblockMatch> find(BlockGetter level, BlockPos origin) {
        return MultiblockDetector.find(this, level, origin);
    }

    /** Checks only {@code rotation} — see {@link MultiblockDetector#find(Multiblock, BlockGetter, BlockPos, PatternRotation)}. */
    public Optional<MultiblockMatch> find(BlockGetter level, BlockPos origin, PatternRotation rotation) {
        return MultiblockDetector.find(this, level, origin, rotation);
    }

    @Override
    public String toString() {
        return "Multiblock[" + name + "]";
    }

    public static final class Builder {

        private final String name;
        private final List<String[]> layers = new ArrayList<>();
        private final Map<Character, PatternMatcher> matchers = new HashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        /** Adds one Y-level, bottom-to-top call order — see {@link Multiblock}'s coordinate convention. */
        public Builder layer(String... rows) {
            layers.add(rows);
            return this;
        }

        /** Registers what pattern character {@code symbol} means. */
        public Builder where(char symbol, PatternMatcher matcher) {
            matchers.put(symbol, Objects.requireNonNull(matcher, "matcher"));
            return this;
        }

        /** Validates the accumulated layers/matchers and builds the immutable {@link Multiblock} — see {@link MultiblockPattern#compile}. */
        public Multiblock build() {
            MultiblockPattern pattern = MultiblockPattern.compile(name, layers, matchers);
            return new Multiblock(name, pattern);
        }
    }
}
