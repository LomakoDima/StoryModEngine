package com.dimalab.storymodengine.common.model.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds which model a placed {@link ModelBlock} shows. Only the <b>name</b> is stored and synced —
 * geometry never crosses the network, since both sides already have the file; the same principle
 * {@code ModelEntity} and {@code DialogueStepPacket} follow.
 *
 * <p>{@link #getUpdatePacket}/{@link #getUpdateTag} are what make a placed block appear for players
 * who weren't nearby when it was set: without them the client would have a block entity with an
 * empty model name and draw nothing.
 */
public class ModelBlockEntity extends BlockEntity {

    private String modelName = "";

    public ModelBlockEntity(BlockPos pos, BlockState state) {
        super(ModelBlocks.MODEL_BLOCK_ENTITY.get(), pos, state);
    }

    public String modelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
        setChanged();
        if (level != null) {
            // Re-send to nearby clients and make the collision shape re-resolve for the new model.
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("ModelName", modelName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        modelName = tag.getString("ModelName");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("ModelName", modelName);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            load(packet.getTag());
        }
    }
}
