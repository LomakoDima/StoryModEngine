package com.dimalab.storymodengine.common.network.serialization;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reads and writes exactly one type to/from a {@link FriendlyByteBuf} — the single primitive the
 * whole automatic serialization system is built from, the same one-method-interface shape {@code
 * math.interp.Interpolator<T>} already uses in this engine for the same reason: every composite
 * case ({@code List<T>}, a record's fields, an array) is nothing but this called repeatedly, never
 * a special case of its own.
 */
public interface Serializer<T> {

    void write(T value, FriendlyByteBuf buf);

    T read(FriendlyByteBuf buf);

    /**
     * Encodes to a plain byte array rather than a live {@link FriendlyByteBuf} — the bridge {@code
     * capabilities} uses to store the exact same encoding both in NBT ({@code CompoundTag#putByteArray})
     * and on the wire, one code path for disk and network instead of two.
     */
    default byte[] toBytes(T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write(value, buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /** The inverse of {@link #toBytes} — reads a value back from bytes produced by it. */
    default T fromBytes(byte[] bytes) {
        return read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
    }

    /** Builds a {@link Serializer} from a writer/reader pair — the usual way to define one. */
    static <T> Serializer<T> of(BiConsumer<T, FriendlyByteBuf> writer, Function<FriendlyByteBuf, T> reader) {
        return new Serializer<>() {
            @Override
            public void write(T value, FriendlyByteBuf buf) {
                writer.accept(value, buf);
            }

            @Override
            public T read(FriendlyByteBuf buf) {
                return reader.apply(buf);
            }
        };
    }
}
