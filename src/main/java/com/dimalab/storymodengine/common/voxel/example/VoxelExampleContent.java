package com.dimalab.storymodengine.common.voxel.example;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/**
 * Registers {@link ExampleMachineBlock} the same way any mod author's own content would (see
 * {@code ModItems.java}) — {@code EXAMPLE_MACHINE} becomes id {@code example_machine}, and it gets
 * a {@code BlockItem} for free via {@code ContentTypeRegistry}'s existing {@code Block} expander.
 * No new registration machinery for the demo.
 */
public final class VoxelExampleContent {

    @AutoContent
    public static final Supplier<Block> EXAMPLE_MACHINE = ContentHolder.of(() -> new ExampleMachineBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.5f, 6.0f)));
}
