package com.dimalab.storymodengine.common.multiblock.matcher;

import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Matches any block belonging to a {@link TagKey}, e.g. {@code BlockTags.STONE}. */
public record TagBlockMatcher(TagKey<Block> tag) implements PatternMatcher {

    @Override
    public boolean matches(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(tag);
    }
}
