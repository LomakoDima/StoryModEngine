package com.dimalab.storymodengine.common.multiblock;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stateless algorithm behind {@link Multiblock#find} — kept separate from {@link
 * Multiblock} itself so the definition stays a pure, immutable data holder (see {@code
 * ARCHITECTURE.md}'s "why Multiblock holds no runtime state") while the actual scanning logic
 * is independently reusable/testable. Detection is always explicit and on-demand: called once
 * per check, never wired into a tick loop or a world-wide scan — see the class-level rationale
 * in {@code ARCHITECTURE.md}.
 */
public final class MultiblockDetector {

    private MultiblockDetector() {
    }

    /** Tries every {@link PatternRotation} in {@link PatternRotation#values()} order, returning the first that matches. */
    public static Optional<MultiblockMatch> find(Multiblock multiblock, BlockGetter level, BlockPos origin) {
        for (PatternRotation rotation : PatternRotation.values()) {
            Optional<MultiblockMatch> match = find(multiblock, level, origin, rotation);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** Checks only {@code rotation} — no other position or orientation is tried. */
    public static Optional<MultiblockMatch> find(Multiblock multiblock, BlockGetter level, BlockPos origin, PatternRotation rotation) {
        EngineLog.channel("Multiblock").debug("Checking multiblock '{}' at {} ({})", multiblock.name(), origin, rotation);

        MultiblockPattern pattern = multiblock.pattern();
        List<BlockPos> positions = new ArrayList<>(pattern.width() * pattern.height() * pattern.depth());
        for (int y = 0; y < pattern.height(); y++) {
            for (int z = 0; z < pattern.depth(); z++) {
                for (int x = 0; x < pattern.width(); x++) {
                    BlockPos worldPos = rotation.toWorldPos(origin, x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    if (!pattern.matcherAt(x, y, z).matches(level, worldPos, state)) {
                        return Optional.empty();
                    }
                    positions.add(worldPos);
                }
            }
        }

        MultiblockMatch match = new MultiblockMatch(multiblock, origin, rotation, positions);
        EngineLog.channel("Multiblock").info("Multiblock '{}' matched at {} ({})", multiblock.name(), origin, rotation);
        return Optional.of(match);
    }
}
