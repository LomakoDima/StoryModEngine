package com.dimalab.storymodengine.common.multiblock;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A successful {@link Multiblock#find} result — never just a {@code boolean}, since a caller
 * needs the actual world positions to do anything useful with a match (highlight the structure,
 * replace blocks, look up a {@code BlockEntity}, start a machine, trigger a Story Flow, ...).
 * {@code Multiblock} itself does none of that — see its Javadoc.
 *
 * @param multiblock the definition that matched
 * @param origin     the world position pattern-local {@code (0, 0, 0)} mapped to
 * @param rotation   which of the four horizontal orientations matched
 * @param positions  every matched world position, in pattern-iteration order (layer, then row, then column) — one entry per pattern cell, including air/any cells
 */
public record MultiblockMatch(Multiblock multiblock, BlockPos origin, PatternRotation rotation, List<BlockPos> positions) {

    public MultiblockMatch {
        positions = List.copyOf(positions);
    }
}
