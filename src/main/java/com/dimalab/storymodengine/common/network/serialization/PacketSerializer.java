package com.dimalab.storymodengine.common.network.serialization;

import net.minecraft.network.FriendlyByteBuf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a {@link Serializer} for a {@code record} type by composing one {@link Serializer} per
 * record component (resolved via {@link SerializerRegistry#resolve}) — {@code write} calls each
 * component's own accessor and writes it in declaration order, {@code read} reads each component
 * back in the same order and hands the values straight to the record's canonical constructor. This
 * one method is what turns "a {@code @Packet} record" into working encode/decode with no per-packet
 * code, and — since it's keyed only on "is this a record", not "is this annotated {@code @Packet}"
 * — it's also what makes an arbitrary nested record field (a custom data type, or {@code
 * math.transform.Transform}) serialize automatically the moment its own components resolve.
 *
 * <p>Uses {@code MethodHandles} rather than repeated {@code Constructor#newInstance}/{@code
 * Method#invoke} reflection — built once per record type and cached, then reused for every
 * packet instance, so the per-message cost is a handle invocation, not a fresh reflective lookup.
 */
public final class PacketSerializer {

    private static final Map<Class<?>, Serializer<?>> CACHE = new ConcurrentHashMap<>();

    private PacketSerializer() {
    }

    /**
     * Deliberately not {@code CACHE.computeIfAbsent(recordType, PacketSerializer::build)}: {@link
     * #build} can itself resolve a nested record field whose own type also needs {@link #forRecord}
     * (a record containing another record component) — a second {@code computeIfAbsent} call on the
     * *same* {@link ConcurrentHashMap} from inside another one's mapping function is a documented JDK
     * trap: {@code ConcurrentHashMap} throws {@code IllegalStateException("Recursive update")} for
     * that nesting non-deterministically, depending on the map's internal bin layout — this is the
     * exact bug already documented and worked around in {@link PojoSerializer#forClass}, just never
     * mirrored here. Plain {@code get}/{@code putIfAbsent} carry no such restriction, so the same
     * small double-checked cache applies — {@link #build} may run twice for the same type under a
     * genuine race, which is harmless (it's pure/stateless).
     */
    @SuppressWarnings("unchecked")
    public static <T> Serializer<T> forRecord(Class<T> recordType) {
        Serializer<?> cached = CACHE.get(recordType);
        if (cached != null) {
            return (Serializer<T>) cached;
        }
        Serializer<?> built = build(recordType);
        Serializer<?> existing = CACHE.putIfAbsent(recordType, built);
        return (Serializer<T>) (existing != null ? existing : built);
    }

    private static Serializer<?> build(Class<?> recordType) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + " is not a record — "
                    + "@Packet types (and any nested field type without its own registered "
                    + "serializer) must be records, since a record's components are its entire "
                    + "enumerable state");
        }

        RecordComponent[] components = recordType.getRecordComponents();
        int count = components.length;
        Serializer<Object>[] componentSerializers = new Serializer[count];
        MethodHandle[] accessors = new MethodHandle[count];
        Class<?>[] componentTypes = new Class<?>[count];

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            for (int i = 0; i < count; i++) {
                RecordComponent component = components[i];
                componentTypes[i] = component.getType();
                componentSerializers[i] = SerializerRegistry.resolve(component.getGenericType());
                Method accessor = component.getAccessor();
                accessors[i] = lookup.unreflect(accessor);
            }
            Constructor<?> canonical = recordType.getDeclaredConstructor(componentTypes);
            MethodHandle constructor = lookup.unreflectConstructor(canonical);

            return Serializer.of(
                    (Object value, FriendlyByteBuf buf) -> {
                        try {
                            for (int i = 0; i < count; i++) {
                                Object component = accessors[i].invoke(value);
                                componentSerializers[i].write(component, buf);
                            }
                        } catch (Throwable t) {
                            throw new IllegalStateException("Failed to encode " + recordType.getName(), t);
                        }
                    },
                    buf -> {
                        try {
                            Object[] args = new Object[count];
                            for (int i = 0; i < count; i++) {
                                args[i] = componentSerializers[i].read(buf);
                            }
                            return constructor.invokeWithArguments(args);
                        } catch (Throwable t) {
                            throw new IllegalStateException("Failed to decode " + recordType.getName(), t);
                        }
                    });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build a serializer for record " + recordType.getName(), e);
        }
    }
}
