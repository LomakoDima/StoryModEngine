package com.dimalab.storymodengine.common.multiblock.example;

import com.dimalab.storymodengine.common.multiblock.Multiblock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.dimalab.storymodengine.common.multiblock.PatternMatchers.air;
import static com.dimalab.storymodengine.common.multiblock.PatternMatchers.block;

/**
 * The structure {@code /sme multiblock} demonstrates live. Deliberately
 * <b>asymmetric</b> — not the fully-symmetric altar shown in {@code Multiblock}'s own Javadoc —
 * because a rotationally-symmetric pattern can't actually prove rotation detection works: a
 * broken (or no-op) rotation transform would still "accidentally" match a symmetric layout from
 * every orientation. Here, the diamond block sits off-center (west edge), so only the one
 * orientation the structure was actually built in reports a match, with the correct {@code
 * PatternRotation} — that specificity is the live proof.
 *
 * <pre>
 * Z=0: OOO
 * Z=1: DAO   (D = diamond block, fixed on the west/X=0 edge)
 * Z=2: OOO
 * </pre>
 */
public final class MultiblockExamples {

    /** Single layer, row index = Z, column index = X — see {@link Multiblock}'s coordinate convention. */
    public static final String[] DEMO_LAYER = {"OOO", "DAO", "OOO"};

    public static final Multiblock DEMO = Multiblock.define("demo_ring")
            .layer(DEMO_LAYER)
            .where('O', block(Blocks.OBSIDIAN))
            .where('D', block(Blocks.DIAMOND_BLOCK))
            .where('A', air())
            .build();

    private MultiblockExamples() {
    }

    /**
     * What {@link com.dimalab.storymodengine.common.multiblock.example.MultiblockDemoCommand} places
     * for each pattern symbol when building/clearing the demo structure in the world —
     * deliberately kept out of {@link #DEMO} itself: a {@code Multiblock} only ever checks a
     * pattern, it never places blocks.
     */
    public static Block blockFor(char symbol) {
        return switch (symbol) {
            case 'O' -> Blocks.OBSIDIAN;
            case 'D' -> Blocks.DIAMOND_BLOCK;
            case 'A' -> Blocks.AIR;
            default -> throw new IllegalArgumentException("No demo block registered for symbol '" + symbol + "'");
        };
    }
}
