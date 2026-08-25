package com.dimalab.storymodengine.common.network.serialization.serializers;

import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The primitive/{@code String} {@link Serializer}s every other serializer, ultimately, is built
 * from. Registered for both the primitive class and its boxed wrapper (a record component can be
 * declared either way — {@code int} and {@code Integer} both resolve to a working serializer).
 *
 * <p>{@code int}/{@code long} use Minecraft's own {@code VarInt}/{@code VarLong} encoding (the
 * same choice the rest of the protocol makes almost everywhere) rather than fixed-width — smaller
 * on the wire for the small values most gameplay data actually is.
 */
public final class PrimitiveSerializers {

    private PrimitiveSerializers() {
    }

    public static void registerAll() {
        register(boolean.class, Boolean.class, FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
        register(byte.class, Byte.class, (buf, v) -> buf.writeByte(v), buf -> buf.readByte());
        register(short.class, Short.class, (buf, v) -> buf.writeShort(v), buf -> buf.readShort());
        register(char.class, Character.class, (buf, v) -> buf.writeChar(v), buf -> buf.readChar());
        register(int.class, Integer.class, FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
        register(long.class, Long.class, FriendlyByteBuf::writeVarLong, FriendlyByteBuf::readVarLong);
        register(float.class, Float.class, FriendlyByteBuf::writeFloat, FriendlyByteBuf::readFloat);
        register(double.class, Double.class, FriendlyByteBuf::writeDouble, FriendlyByteBuf::readDouble);
        SerializerRegistry.register(String.class, Serializer.of((v, buf) -> buf.writeUtf(v), buf -> buf.readUtf()));
    }

    @SuppressWarnings("unchecked")
    private static <P, B> void register(Class<P> primitive, Class<B> boxed,
                                         WriteOp<P> writer, Function<FriendlyByteBuf, P> reader) {
        Serializer<P> serializer = Serializer.of((value, buf) -> writer.write(buf, value), reader);
        SerializerRegistry.register(primitive, serializer);
        SerializerRegistry.register(boxed, (Serializer<B>) (Serializer<?>) serializer);
    }

    @FunctionalInterface
    private interface WriteOp<P> {
        void write(FriendlyByteBuf buf, P value);
    }
}
