package com.dimalab.storymodengine.common.voxel.example;

import com.dimalab.storymodengine.common.voxel.ModelShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mandatory {@code voxel} package demo: a "machine" block whose model has two cuboids (a base
 * plus an offset vent, see {@code assets/storymodengine/models/block/example_machine.json}) instead
 * of a plain cube, so its rotated outline is visibly asymmetric. {@link #getShape} is the whole
 * point — one call to {@link ModelShapes}, no hand-written {@code SHAPE_NORTH}/{@code SHAPE_EAST}/
 * {@code SHAPE_SOUTH}/{@code SHAPE_WEST} constants. {@code FACING} is inherited straight from
 * {@link HorizontalDirectionalBlock}, not redeclared.
 */
public final class ExampleMachineBlock extends HorizontalDirectionalBlock {

    private static final ResourceLocation MODEL_ID = new ResourceLocation("storymodengine", "block/example_machine");

    public ExampleMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModelShapes.get(MODEL_ID, state.getValue(FACING));
    }
}
