package com.dimalab.storymodengine.common.dialogue.discovery;

import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.api.dialogue.annotation.AutoDialogue;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Finds every {@code @AutoDialogue} field in one mod's own jar via Forge's annotation scan data —
 * byte-for-byte the same shape as {@code flow.discovery.FlowDiscovery}, here requiring a {@code
 * static DialogueDefinition}.
 */
public final class DialogueDiscovery {

    private static final org.objectweb.asm.Type AUTO_DIALOGUE = org.objectweb.asm.Type.getType(AutoDialogue.class);

    private DialogueDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.FIELD || !AUTO_DIALOGUE.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data)) {
                discovered++;
            }
        }

        EngineLog.channel("Dialogue").info("[{}] @AutoDialogue discovered {} definition(s)", modId, discovered);
    }

    private static boolean registerField(String modId, ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, DialogueDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !DialogueDefinition.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Dialogue").warn(
                        "[{}] Skipping @AutoDialogue on {}.{} — field must be a static DialogueDefinition",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof DialogueDefinition definition)) {
                EngineLog.channel("Dialogue").warn("[{}] Skipping @AutoDialogue on {}.{} — field value is null", modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            DialogueRegistry.register(definition);
            EngineLog.channel("Dialogue").debug("[{}] @AutoDialogue registered '{}'", modId, definition.id());
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process @AutoDialogue on " + ownerClassName + "." + fieldName, e);
        }
    }
}
