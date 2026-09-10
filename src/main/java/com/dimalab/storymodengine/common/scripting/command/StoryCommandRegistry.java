package com.dimalab.storymodengine.common.scripting.command;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.server.level.ServerPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code name -> bound @StoryCommand}. Binds via {@code MethodHandles.lookup().unreflect(method)} —
 * the same idiom {@code common.event.EventBus#bind} already uses for {@code @SubscribeEvent}, for
 * consistency and to avoid {@code Method#invoke}'s per-call reflection overhead.
 */
public final class StoryCommandRegistry {

    private static final Map<String, CommandHandle> COMMANDS = new ConcurrentHashMap<>();

    private StoryCommandRegistry() {
    }

    public static void register(String name, Method method) {
        try {
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            List<Class<?>> paramTypes = List.of(method.getParameterTypes()).subList(1, method.getParameterCount());
            COMMANDS.put(name, new CommandHandle(handle, paramTypes));
        } catch (ReflectiveOperationException e) {
            EngineLog.channel("SME").warn("Failed to bind @StoryCommand '{}': {}", name, e.toString());
        }
    }

    public static CommandHandle get(String name) {
        return COMMANDS.get(name);
    }

    public static void invoke(String name, ServerPlayer player, List<Object> coercedArgs) {
        CommandHandle handle = COMMANDS.get(name);
        if (handle == null) {
            EngineLog.channel("SME").error("Unknown story command '{}'", name);
            return;
        }
        try {
            List<Object> all = new ArrayList<>(coercedArgs.size() + 1);
            all.add(player);
            all.addAll(coercedArgs);
            handle.handle().invokeWithArguments(all);
        } catch (Throwable t) {
            EngineLog.channel("SME").error("Story command '" + name + "' threw", t);
        }
    }

    public record CommandHandle(MethodHandle handle, List<Class<?>> paramTypes) {
    }
}
