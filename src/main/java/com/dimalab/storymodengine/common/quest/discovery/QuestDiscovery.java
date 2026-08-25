package com.dimalab.storymodengine.common.quest.discovery;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.api.quest.annotation.AutoQuest;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Scans for {@code static QuestDefinition} fields annotated {@code @AutoQuest} — byte-for-byte the
 * same shape as {@code dialogue.discovery.DialogueDiscovery}/{@code flow.discovery.FlowDiscovery}
 * (verified against the real {@code DialogueDiscovery} source), copied deliberately rather than
 * invented fresh, per the task's own instruction not to guess this pattern.
 */
public final class QuestDiscovery {

    private static final org.objectweb.asm.Type AUTO_QUEST = org.objectweb.asm.Type.getType(AutoQuest.class);

    private QuestDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();
        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != java.lang.annotation.ElementType.FIELD || !AUTO_QUEST.equals(data.annotationType())) {
                continue;
            }
            if (registerField(data)) {
                discovered++;
            }
        }
        EngineLog.channel("Quest").info("[{}] @AutoQuest discovered {} definition(s)", modId, discovered);
    }

    private static boolean registerField(ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, QuestDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !QuestDefinition.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Quest").warn("@AutoQuest on {}.{} must be a static QuestDefinition field — skipping", ownerClassName, fieldName);
                return false;
            }
            Object value = field.get(null);
            if (!(value instanceof QuestDefinition definition)) {
                EngineLog.channel("Quest").warn("@AutoQuest on {}.{} — field value is null, skipping", ownerClassName, fieldName);
                return false;
            }
            QuestRegistry.register(definition);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process @AutoQuest on " + ownerClassName + "." + fieldName, e);
        }
    }
}
