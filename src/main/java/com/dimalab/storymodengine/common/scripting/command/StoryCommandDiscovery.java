package com.dimalab.storymodengine.common.scripting.command;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds every {@code @StoryCommand} method in one mod's own jar via Forge's {@code ModFileScanData}
 * — byte-for-byte the same shape as {@code common.event.discovery.EventListenerDiscovery}, just
 * targeting {@link StoryCommand} instead of {@code @SubscribeEvent}.
 */
public final class StoryCommandDiscovery {

    private static final org.objectweb.asm.Type STORY_COMMAND = org.objectweb.asm.Type.getType(StoryCommand.class);

    private StoryCommandDiscovery() {
    }

    public static void run(String modId) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        Set<String> ownerClassNames = new LinkedHashSet<>();
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.METHOD || !STORY_COMMAND.equals(data.annotationType())) {
                continue;
            }
            ownerClassNames.add(data.clazz().getClassName());
        }

        int discovered = 0;
        for (String className : ownerClassNames) {
            try {
                Class<?> owner = Class.forName(className, true, StoryCommandDiscovery.class.getClassLoader());
                discovered += bindAll(owner);
            } catch (ClassNotFoundException e) {
                EngineLog.channel("SME").warn("[{}] Failed to load @StoryCommand owner {}: {}", modId, className, e.toString());
            }
        }
        EngineLog.channel("SME").info("[{}] @StoryCommand discovered {} command(s)", modId, discovered);
    }

    private static int bindAll(Class<?> owner) {
        int count = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(StoryCommand.class)) {
                continue;
            }
            String name = method.getAnnotation(StoryCommand.class).value();
            if (!validate(owner, method)) {
                continue;
            }
            StoryCommandRegistry.register(name, method);
            count++;
        }
        return count;
    }

    private static boolean validate(Class<?> owner, Method method) {
        if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
            warn(owner, method, "must be public static");
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 1 || params[0] != ServerPlayer.class) {
            warn(owner, method, "first parameter must be ServerPlayer");
            return false;
        }
        for (int i = 1; i < params.length; i++) {
            Class<?> p = params[i];
            if (p != String.class && p != int.class && p != double.class && p != boolean.class && p != long.class) {
                warn(owner, method, "parameter " + i + " (" + p.getSimpleName() + ") must be String/int/double/boolean/long");
                return false;
            }
        }
        if (method.getReturnType() != void.class) {
            warn(owner, method, "must return void");
            return false;
        }
        return true;
    }

    private static void warn(Class<?> owner, Method method, String reason) {
        EngineLog.channel("SME").warn("Skipping @StoryCommand {}.{} — {}", owner.getSimpleName(), method.getName(), reason);
    }
}
