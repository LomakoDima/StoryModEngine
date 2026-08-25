package com.dimalab.storymodengine.common.network.serialization.serializers;

import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The JOML type {@link Serializer}s — {@code math} uses these directly rather than wrapping them
 * in engine-specific types (see {@code ARCHITECTURE.md}), so this is what "networking must be able
 * to automatically serialize math types" means concretely: {@code Vector3f}/{@code Quaternionf}
 * delegate to {@code FriendlyByteBuf}'s own {@code writeVector3f}/{@code readVector3f} and {@code
 * writeQuaternion}/{@code readQuaternion} (verified against source — no reimplementation);
 * {@code Vector2f}/{@code Vector4f} have no built-in equivalent, so they're written component-wise
 * by hand, the same shape as {@code Vec2}/{@code Vec3} in {@link MinecraftSerializers}.
 *
 * <p>{@code math.transform.Transform} needs no entry here at all: it's a plain {@code record
 * Transform(Vector3f translation, Quaternionf rotation, Vector3f scale)}, so once these three are
 * registered, {@code SerializerRegistry}'s record fallback composes a working {@code
 * Serializer<Transform>} automatically — the same free ride any other record gets. "Color" in this
 * engine is a packed {@code int} (ARGB, see {@code Interpolators.ARGB_COLOR}), already covered by
 * {@link PrimitiveSerializers}.
 */
public final class MathSerializers {

    private MathSerializers() {
    }

    public static void registerAll() {
        SerializerRegistry.register(Vector3f.class, Serializer.of(
                (v, buf) -> buf.writeVector3f(v), buf -> buf.readVector3f()));

        SerializerRegistry.register(Quaternionf.class, Serializer.of(
                (v, buf) -> buf.writeQuaternion(v), buf -> buf.readQuaternion()));

        SerializerRegistry.register(Vector2f.class, Serializer.of(
                (v, buf) -> {
                    buf.writeFloat(v.x);
                    buf.writeFloat(v.y);
                },
                buf -> new Vector2f(buf.readFloat(), buf.readFloat())));

        SerializerRegistry.register(Vector4f.class, Serializer.of(
                (v, buf) -> {
                    buf.writeFloat(v.x);
                    buf.writeFloat(v.y);
                    buf.writeFloat(v.z);
                    buf.writeFloat(v.w);
                },
                buf -> new Vector4f(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())));
    }
}
