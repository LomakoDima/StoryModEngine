package com.dimalab.storymodengine.common.flow.discovery;

import com.dimalab.storymodengine.common.flow.FlowDefinition;
import com.dimalab.storymodengine.api.flow.annotation.AutoFlow;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Finds every {@code @AutoFlow} field in one mod's own jar via Forge's annotation scan data — the
 * same {@code ModFileScanData} mechanism {@code CapabilityDiscovery}/{@code PacketDiscovery}/{@code
 * ContentDiscovery}/{@code EventListenerDiscovery} already use, here filtered to {@code FIELD} and
 * {@link AutoFlow}, requiring a {@code static FlowDefinition}.
 */
public final class FlowDiscovery {

    private static final org.objectweb.asm.Type AUTO_FLOW = org.objectweb.asm.Type.getType(AutoFlow.class);

    private FlowDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.FIELD || !AUTO_FLOW.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data)) {
                discovered++;
            }
        }

        EngineLog.channel("Flow").info("[{}] @AutoFlow discovered {} definition(s)", modId, discovered);
    }

    private static boolean registerField(String modId, ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, FlowDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !FlowDefinition.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Flow").warn(
                        "[{}] Skipping @AutoFlow on {}.{} — field must be a static FlowDefinition",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof FlowDefinition definition)) {
                EngineLog.channel("Flow").warn("[{}] Skipping @AutoFlow on {}.{} — field value is null", modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            FlowRegistry.register(definition);
            EngineLog.channel("Flow").debug("[{}] @AutoFlow registered '{}'", modId, definition.id());
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process @AutoFlow on " + ownerClassName + "." + fieldName, e);
        }
    }
}
