package com.dimalab.storymodengine.common.multiblock;

import com.dimalab.storymodengine.common.multiblock.matcher.AirMatcher;
import com.dimalab.storymodengine.common.multiblock.matcher.AnyBlockMatcher;
import com.dimalab.storymodengine.common.multiblock.matcher.BlockStateMatcher;
import com.dimalab.storymodengine.common.multiblock.matcher.ExactBlockMatcher;
import com.dimalab.storymodengine.api.multiblock.matcher.PatternMatcher;
import com.dimalab.storymodengine.common.multiblock.matcher.TagBlockMatcher;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The declarative surface every {@code where(char, ...)} call in a pattern definition is built
 * from — meant to be statically imported ({@code import static ...PatternMatchers.*;}) so a
 * pattern reads as {@code where('O', block(Blocks.OBSIDIAN))}, not
 * {@code where('O', PatternMatchers.block(Blocks.OBSIDIAN))}.
 */
public final class PatternMatchers {

    private PatternMatchers() {
    }

    /** Matches any {@link BlockState} of {@code block} — properties are ignored. */
    public static PatternMatcher block(Block block) {
        return new ExactBlockMatcher(block);
    }

    /** Matches one exact {@link BlockState}, properties included. */
    public static PatternMatcher state(BlockState state) {
        return new BlockStateMatcher(state);
    }

    /** Matches any block belonging to {@code tag}. */
    public static PatternMatcher tag(TagKey<Block> tag) {
        return new TagBlockMatcher(tag);
    }

    /** Matches only air. */
    public static PatternMatcher air() {
        return AirMatcher.INSTANCE;
    }

    /** Matches anything — a "don't care" cell. */
    public static PatternMatcher any() {
        return AnyBlockMatcher.INSTANCE;
    }
}
