package com.dimalab.storymodengine.common.multiblock.matcher;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Matches anything — a "don't care" cell. Stateless, so one shared instance is enough (see {@link #INSTANCE}). */
public final class AnyBlockMatcher implements PatternMatcher {

    public static final AnyBlockMatcher INSTANCE = new AnyBlockMatcher();

    private AnyBlockMatcher() {
    }

    @Override
    public boolean matches(BlockGetter level, BlockPos pos, BlockState state) {
        return true;
    }
}
