package com.dimalab.storymodengine.common.multiblock.matcher;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Matches one exact {@link BlockState} — unlike {@link ExactBlockMatcher}, properties matter (e.g. a specific facing). */
public record BlockStateMatcher(BlockState state) implements PatternMatcher {

    @Override
    public boolean matches(BlockGetter level, BlockPos pos, BlockState worldState) {
        return worldState.equals(state);
    }
}
