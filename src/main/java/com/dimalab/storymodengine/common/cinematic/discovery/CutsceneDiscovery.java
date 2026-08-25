package com.dimalab.storymodengine.common.cinematic.discovery;

import com.dimalab.storymodengine.common.cinematic.CutsceneDefinition;
import com.dimalab.storymodengine.api.cinematic.annotation.AutoCutscene;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Finds every {@code @AutoCutscene} field in one mod's own jar via Forge's annotation scan data —
 * the same {@code ModFileScanData} mechanism {@code FlowDiscovery}/{@code CapabilityDiscovery}/
 * {@code PacketDiscovery}/{@code ContentDiscovery} already use, here filtered to {@code FIELD} and
 * {@link AutoCutscene}, requiring a {@code static CutsceneDefinition}.
 */
public final class CutsceneDiscovery {

    private static final org.objectweb.asm.Type AUTO_CUTSCENE = org.objectweb.asm.Type.getType(AutoCutscene.class);

    private CutsceneDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.FIELD || !AUTO_CUTSCENE.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data)) {
                discovered++;
            }
        }

        EngineLog.channel("Cinematic").info("[{}] @AutoCutscene discovered {} definition(s)", modId, discovered);
    }

    private static boolean registerField(String modId, ModFileScanData.AnnotationData data) {
        String ownerClassName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerClassName, true, CutsceneDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !CutsceneDefinition.class.isAssignableFrom(field.getType())) {
                EngineLog.channel("Cinematic").warn(
                        "[{}] Skipping @AutoCutscene on {}.{} — field must be a static CutsceneDefinition",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof CutsceneDefinition definition)) {
                EngineLog.channel("Cinematic").warn(
                        "[{}] Skipping @AutoCutscene on {}.{} — field value is null", modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            CutsceneRegistry.register(definition);
            EngineLog.channel("Cinematic").debug("[{}] @AutoCutscene registered '{}'", modId, definition.id());
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process @AutoCutscene on " + ownerClassName + "." + fieldName, e);
        }
    }
}
