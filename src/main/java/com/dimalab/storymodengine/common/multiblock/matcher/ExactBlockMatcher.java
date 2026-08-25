package com.dimalab.storymodengine.common.multiblock.matcher;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Matches any {@link BlockState} of a specific {@link Block} — properties (facing, waterlogged, ...) are ignored. */
public record ExactBlockMatcher(Block block) implements PatternMatcher {

    @Override
    public boolean matches(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(block);
    }
}
