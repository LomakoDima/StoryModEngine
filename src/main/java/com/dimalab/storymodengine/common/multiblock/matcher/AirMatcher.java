package com.dimalab.storymodengine.common.multiblock.matcher;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Matches only air — stateless, so one shared instance is enough (see {@link #INSTANCE}). */
public final class AirMatcher implements PatternMatcher {

    public static final AirMatcher INSTANCE = new AirMatcher();

    private AirMatcher() {
    }

    @Override
    public boolean matches(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isAir();
    }
}
