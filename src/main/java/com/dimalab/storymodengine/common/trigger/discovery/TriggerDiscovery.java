package com.dimalab.storymodengine.common.trigger.discovery;

import com.dimalab.storymodengine.api.trigger.annotation.AutoTrigger;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import com.dimalab.storymodengine.common.trigger.TriggerSystem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Byte-for-byte the same shape as {@code quest.discovery.QuestDiscovery}/{@code
 * flow.discovery.FlowDiscovery} — scans every {@code @AutoTrigger public static final Trigger}
 * field via the mod's own {@code ModFileScanData}, no manual registration anywhere. The one addition
 * over the {@code QuestDiscovery} template: after registering into {@link TriggerRegistry}, it also
 * calls {@link TriggerSystem#arm} so an {@code EVENT} trigger's {@code EventBus} subscription exists
 * from the moment discovery finishes, not lazily on first use.
 */
public final class TriggerDiscovery {

    private static final org.objectweb.asm.Type AUTO_TRIGGER = org.objectweb.asm.Type.getType(AutoTrigger.class);

    private TriggerDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != java.lang.annotation.ElementType.FIELD || !AUTO_TRIGGER.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data)) {
                discovered++;
            }
        }

        EngineLog.channel("Trigger").info("[{}] @AutoTrigger discovered {} definition(s)", modId, discovered);
    }

    private static boolean registerField(String modId, ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, TriggerDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !Trigger.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Trigger").warn(
                        "[{}] Skipping @AutoTrigger on {}.{} — field must be a static Trigger",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof Trigger trigger)) {
                EngineLog.channel("Trigger").warn("[{}] Skipping @AutoTrigger on {}.{} — field value is null", modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            TriggerRegistry.register(trigger);
            TriggerSystem.arm(trigger);
            EngineLog.channel("Trigger").debug("[{}] @AutoTrigger registered '{}'", modId, trigger.id());
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process @AutoTrigger on " + ownerClassName + "." + fieldName, e);
        }
    }
}
