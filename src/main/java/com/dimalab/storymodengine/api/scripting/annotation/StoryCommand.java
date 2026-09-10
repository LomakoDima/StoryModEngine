package com.dimalab.storymodengine.api.scripting.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static void} method callable from {@code .sme} as {@code <value> arg1 arg2 ...}.
 * {@code StoryCommandDiscovery} finds every such method in a mod's own jar via Forge's {@code
 * ModFileScanData} — the same mechanism {@code @SubscribeEvent} uses (mirrored one-for-one, see
 * {@code common.event.discovery.EventListenerDiscovery}), just targeting a different annotation.
 *
 * <p>Required method shape (violations are skipped with a warning at discovery time, never a hard
 * crash): {@code public static}, first parameter exactly {@code ServerPlayer} (supplied
 * automatically from the calling {@code FlowContext}/{@code DialogueContext} — never authored in
 * {@code .sme}), remaining parameters restricted to {@code String}/{@code int}/{@code double}/
 * {@code boolean}/{@code long} (a closed coercion set — see {@code command.ArgumentCoercion}),
 * {@code void} return. The engine's own built-in commands ({@code give}/{@code teleport}/{@code
 * play_sound}/{@code spawn}/{@code set_block}, in {@code command.BuiltinStoryCommands}) use this
 * exact same annotation — there is one dispatch path for both, not a special case for built-ins.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StoryCommand {

    String value();
}
