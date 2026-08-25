package com.dimalab.storymodengine.common.network.serialization;

import net.minecraft.network.FriendlyByteBuf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a {@link Serializer} for a plain mutable class — {@link PacketSerializer}'s sibling for
 * the one shape records can't cover: data meant to be mutated in place ({@code data.experience++}),
 * which is exactly what {@code capabilities} data classes are. Where {@link PacketSerializer}
 * composes from record components + a canonical constructor, this composes from a class's public,
 * non-static, non-final instance fields (declaration order, via reflection) + a public no-arg
 * constructor — {@code write} reads each field, {@code read} constructs a fresh instance and sets
 * each field back. Every leaf type still resolves through {@link SerializerRegistry#resolve}, so
 * this adds no new leaf serializers of its own; it is the one additional *composing* strategy
 * {@code capabilities} needs on top of the same registry {@code network} already built, not a
 * second serialization framework.
 */
public final class PojoSerializer {

    private static final Map<Class<?>, Serializer<?>> CACHE = new ConcurrentHashMap<>();

    private PojoSerializer() {
    }

    /**
     * Deliberately not {@code CACHE.computeIfAbsent(type, PojoSerializer::build)}: {@link #build}
     * can itself resolve a nested field whose own type also needs {@link #forClass} (a POJO
     * capability holding another POJO, e.g. {@code Map<String, FlowState>} inside {@code
     * StoryFlowData}) — a second {@code computeIfAbsent} call on the *same* {@link ConcurrentHashMap}
     * from inside another one's mapping function is a documented JDK trap: {@code
     * ConcurrentHashMap} throws {@code IllegalStateException("Recursive update")} for that nesting
     * regardless of whether the two keys are actually the same, non-deterministically depending on
     * the map's internal bin layout — verified live (passed on one run, failed the next, identical
     * bytecode both times). Plain {@code get}/{@code putIfAbsent} carry no such restriction, so a
     * small double-checked-cache replaces the single atomic call — {@link #build} may run twice
     * for the same type under a genuine race, which is harmless (it's pure/stateless) and far
     * cheaper than the alternative of forbidding nested POJOs.
     */
    @SuppressWarnings("unchecked")
    public static <T> Serializer<T> forClass(Class<T> type) {
        Serializer<?> cached = CACHE.get(type);
        if (cached != null) {
            return (Serializer<T>) cached;
        }
        Serializer<?> built = build(type);
        Serializer<?> existing = CACHE.putIfAbsent(type, built);
        return (Serializer<T>) (existing != null ? existing : built);
    }

    /** Whether {@link #forClass} can actually build a serializer for {@code type} — a record, an interface, or a class with no public no-arg constructor cannot. */
    public static boolean supports(Class<?> type) {
        if (type.isRecord() || type.isInterface() || type.isEnum() || type.isArray() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Serializer<?> build(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                fields.add(field);
            }
        }

        int count = fields.size();
        Serializer<Object>[] fieldSerializers = new Serializer[count];
        MethodHandle[] getters = new MethodHandle[count];
        MethodHandle[] setters = new MethodHandle[count];

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            for (int i = 0; i < count; i++) {
                Field field = fields.get(i);
                fieldSerializers[i] = SerializerRegistry.resolve(field.getGenericType());
                getters[i] = lookup.unreflectGetter(field);
                setters[i] = lookup.unreflectSetter(field);
            }
            Constructor<?> noArgs = type.getConstructor();
            MethodHandle constructor = lookup.unreflectConstructor(noArgs);

            return Serializer.of(
                    (Object value, FriendlyByteBuf buf) -> {
                        try {
                            for (int i = 0; i < count; i++) {
                                Object fieldValue = getters[i].invoke(value);
                                fieldSerializers[i].write(fieldValue, buf);
                            }
                        } catch (Throwable t) {
                            throw new IllegalStateException("Failed to encode " + type.getName(), t);
                        }
                    },
                    buf -> {
                        try {
                            Object instance = constructor.invoke();
                            for (int i = 0; i < count; i++) {
                                Object fieldValue = fieldSerializers[i].read(buf);
                                setters[i].invoke(instance, fieldValue);
                            }
                            return instance;
                        } catch (Throwable t) {
                            throw new IllegalStateException("Failed to decode " + type.getName(), t);
                        }
                    });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build a serializer for " + type.getName()
                    + " — needs a public no-arg constructor and only public, non-final instance fields", e);
        }
    }
}
