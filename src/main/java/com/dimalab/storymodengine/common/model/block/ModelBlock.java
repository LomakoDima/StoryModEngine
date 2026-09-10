package com.dimalab.storymodengine.common.model.block;

import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/**
 * A block whose collision is the actual shape of a glTF model — this is where {@code
 * physics.ModelVoxelizer} pays off, and the one place shaped (multi-box) collision is possible at
 * all: vanilla gives blocks a full {@code VoxelShape} while entities get a single box.
 *
 * <p>Which model a given block shows is per-placement state, held by {@link ModelBlockEntity}, so one
 * registered block serves every model rather than needing one block type per asset. The shape is
 * therefore resolved from the block entity at query time — cached per model id inside {@link
 * ModelPhysics}, so this stays a map lookup rather than re-voxelizing on every collision test.
 *
 * <p>{@link RenderShape#ENTITYBLOCK_ANIMATED} switches off vanilla's own block-model rendering: the
 * mesh is drawn by {@code client.model.ModelBlockRenderer} instead, and without this the generated
 * cube model would render on top of it.
 */
public class ModelBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ModelBlock() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5f)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        String name = modelNameAt(level, pos);
        if (name == null || name.isEmpty()) {
            // Nothing assigned yet — a full cube, so the block is still selectable and breakable
            // rather than becoming an invisible, un-clickable trap.
            return Shapes.block();
        }
        return ModelPhysics.blockShape(name).rotated(state.getValue(FACING)).toVoxelShape();
    }

    /** Collision and outline are the same shape here: the model's own silhouette is what you both bump into and see highlighted. */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static String modelNameAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ModelBlockEntity entity ? entity.modelName() : null;
    }

}
