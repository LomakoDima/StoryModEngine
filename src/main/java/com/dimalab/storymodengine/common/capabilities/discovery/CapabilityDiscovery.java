package com.dimalab.storymodengine.common.capabilities.discovery;

import com.dimalab.storymodengine.common.capabilities.CapabilityData;
import com.dimalab.storymodengine.api.capabilities.OwnerKind;
import com.dimalab.storymodengine.api.capabilities.annotation.Capability;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import com.dimalab.storymodengine.common.capabilities.registry.CapabilityRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.serialization.Serializer;
import com.dimalab.storymodengine.common.network.serialization.SerializerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;

/**
 * Finds every {@code @Capability} field in one mod's own jar via Forge's annotation scan data —
 * the same {@code ModFileScanData} mechanism {@code ContentDiscovery} already uses for {@code
 * @AutoContent} fields and {@code PacketDiscovery} uses for {@code @Packet} types, here filtered
 * to {@link ElementType#FIELD} and {@link Capability}. Discovered fields are validated and
 * resolved into a {@link CapabilityDescriptor}, then added to the global {@link
 * CapabilityRegistry} — everything downstream (attachment, persistence, sync) reads only from
 * that registry, never re-scans anything itself.
 */
public final class CapabilityDiscovery {

    private static final org.objectweb.asm.Type CAPABILITY = org.objectweb.asm.Type.getType(Capability.class);

    private CapabilityDiscovery() {
    }

    /** Scans {@code modId}'s own jar for {@code @Capability} fields and adds them to the global registry. */
    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.FIELD || !CAPABILITY.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data)) {
                discovered++;
            }
        }

        EngineLog.channel("Capabilities").info("[{}] @Capability discovered {} data type(s)", modId, discovered);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerField(String modId, ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, CapabilityDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !CapabilityData.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Capabilities").warn(
                        "[{}] Skipping @Capability on {}.{} — field must be a static CapabilityData<T>",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Type genericType = field.getGenericType();
            if (!(genericType instanceof ParameterizedType parameterized)) {
                EngineLog.channel("Capabilities").warn(
                        "[{}] Skipping @Capability on {}.{} — CapabilityData is missing its type argument",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }
            Class<?> dataType = (Class<?>) parameterized.getActualTypeArguments()[0];

            OwnerKind ownerKind = OwnerKind.of(dataType);
            if (ownerKind == null) {
                EngineLog.channel("Capabilities").warn(
                        "[{}] Skipping @Capability on {}.{} — {} must implement exactly one of "
                                + "EntityCapability/BlockEntityCapability/LevelCapability",
                        modId, ownerClass.getSimpleName(), fieldName, dataType.getSimpleName());
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof CapabilityData<?> handle)) {
                EngineLog.channel("Capabilities").warn(
                        "[{}] Skipping @Capability on {}.{} — field must hold a CapabilityData "
                                + "(declare it as CapabilityData.of(() -> new ...(...)))",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Serializer serializer;
            try {
                serializer = SerializerRegistry.resolve(dataType);
            } catch (RuntimeException e) {
                EngineLog.channel("Capabilities").warn(
                        "[{}] Skipping @Capability on {}.{} — {} is not serializable: {}",
                        modId, ownerClass.getSimpleName(), fieldName, dataType.getSimpleName(), e.toString());
                return false;
            }

            Capability annotation = field.getAnnotation(Capability.class);
            boolean sync = annotation != null && annotation.sync();
            com.dimalab.storymodengine.api.capabilities.SyncAudience audience =
                    annotation != null ? annotation.audience() : com.dimalab.storymodengine.api.capabilities.SyncAudience.AUTO;

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId, fieldName.toLowerCase(Locale.ROOT));
            CapabilityDescriptor descriptor = new CapabilityDescriptor(
                    id, dataType, handle.factory(), ownerKind, sync, audience, serializer);
            CapabilityRegistry.register(descriptor);
            handle.bind(descriptor);

            EngineLog.channel("Capabilities").debug(
                    "[{}] @Capability registered '{}' ({} → {}, sync={})",
                    modId, id, dataType.getSimpleName(), ownerKind, sync);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to process @Capability on " + ownerClassName + "." + fieldName, e);
        }
    }
}
