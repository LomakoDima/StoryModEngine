package com.dimalab.storymodengine.common.network.serialization;

import com.dimalab.storymodengine.common.network.EntityRef;
import com.dimalab.storymodengine.common.network.serialization.serializers.MathSerializers;
import com.dimalab.storymodengine.common.network.serialization.serializers.MinecraftSerializers;
import com.dimalab.storymodengine.common.network.serialization.serializers.PrimitiveSerializers;
import net.minecraft.network.FriendlyByteBuf;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code Class<?>}/generic {@code Type} → {@link Serializer}. Every {@code @Packet} field's
 * serializer ultimately comes from {@link #resolve}, whether it's a direct hit ({@code
 * register(Class, Serializer)} — the extension point the task calls for) or one of the composite
 * shapes this class knows how to build on demand: arrays, enums, {@code List}/{@code Set}/{@code
 * Map}, {@code Optional<T>} (this engine's nullable-value convention — see the class doc below),
 * arbitrary {@code record}s (delegated to {@link PacketSerializer}, which is what makes {@code
 * math.transform.Transform} and any other record type "just work" the moment its own components
 * resolve, with no entry needed here for the record itself), and — for the one shape a record
 * can't cover, data meant to be *mutated in place* — a plain class with a public no-arg
 * constructor and public non-final fields (delegated to {@link PojoSerializer}). This second
 * composing strategy is what {@code capabilities} data classes (e.g. {@code public int
 * experience;}) resolve through, without a separate serialization framework of their own.
 *
 * <p>Resolution is by {@link Type}, not just {@link Class}, specifically so {@code List<UUID>} and
 * {@code List<ItemStack>} — same raw class, different element serializer — resolve correctly; this
 * is exactly the same reflection this engine already leans on in {@code ContentDiscovery}
 * ({@code Field#getGenericType()}) to recover a generic type argument erased from the raw
 * {@code Class}.
 */
public final class SerializerRegistry {

    /**
     * Defensive cap on any collection/map/array size read off the wire, applied before any
     * per-element allocation happens. Not full rate limiting or a real DoS defense (see {@code
     * ARCHITECTURE.md}/the routing layer for that extension point) — just the cheapest possible
     * guard against a malformed or hostile length header driving an enormous allocation from a
     * single packet.
     */
    public static final int MAX_COLLECTION_SIZE = 65536;

    private static final Map<Class<?>, Serializer<?>> BY_CLASS = new ConcurrentHashMap<>();

    static {
        PrimitiveSerializers.registerAll();
        MinecraftSerializers.registerAll();
        MathSerializers.registerAll();
        register(EntityRef.class, Serializer.of((v, buf) -> buf.writeVarInt(v.id()),
                buf -> new EntityRef(buf.readVarInt())));
    }

    private SerializerRegistry() {
    }

    /** Registers (or overrides) the serializer used for {@code type} — the public extension point. */
    public static <T> void register(Class<T> type, Serializer<T> serializer) {
        BY_CLASS.put(type, serializer);
    }

    /** Resolves the serializer for a field's declared type, recursing into generics as needed. */
    @SuppressWarnings("unchecked")
    public static Serializer<Object> resolve(Type type) {
        if (type instanceof Class<?> clazz) {
            return (Serializer<Object>) resolveClass(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Serializer<Object>) resolveParameterized(parameterized);
        }
        throw new IllegalArgumentException("No serializer known for type " + type
                + " — register one via SerializerRegistry.register(...)");
    }

    private static Serializer<?> resolveClass(Class<?> clazz) {
        Serializer<?> direct = BY_CLASS.get(clazz);
        if (direct != null) {
            return direct;
        }
        if (clazz.isArray()) {
            return arraySerializer(clazz);
        }
        if (clazz.isEnum()) {
            return enumSerializer(clazz);
        }
        if (clazz.isRecord()) {
            return PacketSerializer.forRecord(clazz);
        }
        if (PojoSerializer.supports(clazz)) {
            return PojoSerializer.forClass(clazz);
        }
        throw new IllegalArgumentException("No serializer known for " + clazz.getName()
                + " — register one via SerializerRegistry.register(...)");
    }

    @SuppressWarnings("unchecked")
    private static Serializer<?> resolveParameterized(ParameterizedType type) {
        Class<?> raw = (Class<?>) type.getRawType();
        Type[] args = type.getActualTypeArguments();

        if (Optional.class.isAssignableFrom(raw)) {
            Serializer<Object> element = resolve(args[0]);
            return Serializer.of(
                    (Optional<Object> value, FriendlyByteBuf buf) -> {
                        buf.writeBoolean(value.isPresent());
                        value.ifPresent(v -> element.write(v, buf));
                    },
                    buf -> buf.readBoolean() ? Optional.of(element.read(buf)) : Optional.empty());
        }

        if (Map.class.isAssignableFrom(raw)) {
            Serializer<Object> keySerializer = resolve(args[0]);
            Serializer<Object> valueSerializer = resolve(args[1]);
            return Serializer.of(
                    (Map<Object, Object> value, FriendlyByteBuf buf) -> {
                        buf.writeVarInt(value.size());
                        value.forEach((k, v) -> {
                            keySerializer.write(k, buf);
                            valueSerializer.write(v, buf);
                        });
                    },
                    buf -> {
                        int size = readCappedSize(buf);
                        Map<Object, Object> result = new LinkedHashMap<>(size);
                        for (int i = 0; i < size; i++) {
                            result.put(keySerializer.read(buf), valueSerializer.read(buf));
                        }
                        return result;
                    });
        }

        if (Set.class.isAssignableFrom(raw)) {
            Serializer<Object> element = resolve(args[0]);
            return Serializer.of(
                    (Set<Object> value, FriendlyByteBuf buf) -> {
                        buf.writeVarInt(value.size());
                        value.forEach(v -> element.write(v, buf));
                    },
                    buf -> {
                        int size = readCappedSize(buf);
                        Set<Object> result = new HashSet<>(size);
                        for (int i = 0; i < size; i++) {
                            result.add(element.read(buf));
                        }
                        return result;
                    });
        }

        if (List.class.isAssignableFrom(raw) || java.util.Collection.class.isAssignableFrom(raw)) {
            Serializer<Object> element = resolve(args[0]);
            return Serializer.of(
                    (List<Object> value, FriendlyByteBuf buf) -> {
                        buf.writeVarInt(value.size());
                        for (Object v : value) {
                            element.write(v, buf);
                        }
                    },
                    buf -> {
                        int size = readCappedSize(buf);
                        List<Object> result = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            result.add(element.read(buf));
                        }
                        return result;
                    });
        }

        throw new IllegalArgumentException("No serializer known for parameterized type " + type
                + " — register one via SerializerRegistry.register(...)");
    }

    private static int readCappedSize(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("Refusing to decode a collection of size " + size
                    + " (max " + MAX_COLLECTION_SIZE + ") — malformed or hostile packet");
        }
        return size;
    }

    @SuppressWarnings("unchecked")
    private static Serializer<?> arraySerializer(Class<?> arrayType) {
        Class<Object> componentType = (Class<Object>) arrayType.getComponentType();
        Serializer<Object> element = (Serializer<Object>) resolveClass(componentType);
        return Serializer.of(
                (Object value, FriendlyByteBuf buf) -> {
                    int length = Array.getLength(value);
                    buf.writeVarInt(length);
                    for (int i = 0; i < length; i++) {
                        element.write(Array.get(value, i), buf);
                    }
                },
                buf -> {
                    int length = readCappedSize(buf);
                    Object array = Array.newInstance(componentType, length);
                    for (int i = 0; i < length; i++) {
                        Array.set(array, i, element.read(buf));
                    }
                    return array;
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Serializer<?> enumSerializer(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        Serializer<Enum> serializer = Serializer.of(
                (Enum value, FriendlyByteBuf buf) -> buf.writeVarInt(value.ordinal()),
                buf -> {
                    int ordinal = buf.readVarInt();
                    if (ordinal < 0 || ordinal >= constants.length) {
                        throw new IllegalArgumentException("Invalid enum ordinal " + ordinal
                                + " for " + enumType.getName() + " — malformed packet");
                    }
                    return (Enum) constants[ordinal];
                });
        return serializer;
    }
}
