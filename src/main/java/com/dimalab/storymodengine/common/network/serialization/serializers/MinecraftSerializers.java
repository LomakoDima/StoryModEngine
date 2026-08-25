package com.dimalab.storymodengine.common.network.serialization.serializers;

import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The vanilla/Minecraft type {@link Serializer}s. Where {@code FriendlyByteBuf} already has a
 * matching read/write pair ({@code UUID}, {@code ResourceLocation}, {@code BlockPos}, {@code
 * Component}, {@code ItemStack} via {@code readItem}/{@code writeItem}, {@code CompoundTag} via
 * {@code readNbt}/{@code writeNbt}) these delegate straight to it — verified against source, not
 * reimplemented. {@code Vec3}/{@code Vec2} have no such built-in (double/float component pairs,
 * written by hand); {@code BlockState} has none either — it goes through the same global
 * palette id every other part of the protocol uses ({@code Block.BLOCK_STATE_REGISTRY}, verified
 * against {@code Block} source), as a {@code VarInt}.
 */
public final class MinecraftSerializers {

    private MinecraftSerializers() {
    }

    public static void registerAll() {
        SerializerRegistry.register(byte[].class, Serializer.of(
                (v, buf) -> buf.writeByteArray(v), buf -> buf.readByteArray()));

        SerializerRegistry.register(UUID.class, Serializer.of(
                (v, buf) -> buf.writeUUID(v), buf -> buf.readUUID()));

        SerializerRegistry.register(ResourceLocation.class, Serializer.of(
                (v, buf) -> buf.writeResourceLocation(v), buf -> buf.readResourceLocation()));

        SerializerRegistry.register(BlockPos.class, Serializer.of(
                (v, buf) -> buf.writeBlockPos(v), buf -> buf.readBlockPos()));

        SerializerRegistry.register(Component.class, Serializer.of(
                (v, buf) -> buf.writeComponent(v), buf -> buf.readComponent()));

        SerializerRegistry.register(ItemStack.class, Serializer.of(
                (v, buf) -> buf.writeItem(v), buf -> buf.readItem()));

        SerializerRegistry.register(CompoundTag.class, Serializer.of(
                (v, buf) -> buf.writeNbt(v), buf -> buf.readNbt()));

        SerializerRegistry.register(Vec3.class, Serializer.of(
                (v, buf) -> {
                    buf.writeDouble(v.x);
                    buf.writeDouble(v.y);
                    buf.writeDouble(v.z);
                },
                buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

        SerializerRegistry.register(Vec2.class, Serializer.of(
                (v, buf) -> {
                    buf.writeFloat(v.x);
                    buf.writeFloat(v.y);
                },
                buf -> new Vec2(buf.readFloat(), buf.readFloat())));

        SerializerRegistry.register(BlockState.class, Serializer.of(
                (v, buf) -> buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(v)),
                buf -> Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt())));
    }
}
