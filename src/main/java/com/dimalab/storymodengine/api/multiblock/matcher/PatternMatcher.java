package com.dimalab.storymodengine.api.multiblock.matcher;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether one world position satisfies one pattern cell — the entire extension point a
 * {@code Multiblock} pattern is built from. {@link #matches} takes {@link BlockGetter} (not
 * {@code Level}) deliberately: it's the read-only slice of the world API a matcher could ever
 * legitimately need, and it already exposes {@code getBlockEntity(BlockPos)} — so a future
 * BlockEntity-aware matcher (or a capability/fluid one) is just another implementation of this
 * same interface, needing no change here or in {@code MultiblockDetector}.
 */
@FunctionalInterface
public interface PatternMatcher {

    boolean matches(BlockGetter level, BlockPos pos, BlockState state);
}
