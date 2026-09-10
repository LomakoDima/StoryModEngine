package com.dimalab.storymodengine.common.trigger.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.trigger.Trigger;
import com.dimalab.storymodengine.common.trigger.TriggerSystem;
import com.dimalab.storymodengine.common.trigger.persistence.ModTriggerCapabilities;
import com.dimalab.storymodengine.common.trigger.persistence.TriggerPlayerStateData;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code /sme trigger selftest} — this project has no JUnit setup, so this is the same
 * in-game, assert-and-report substitute {@code quest.debug.QuestCompilerTestCommand} already
 * established. Two-tier structure: {@link #testGeometryAndTime} is pure logic (no player/capability
 * touched — {@link Trigger#containsPosition}/{@link Trigger#matchesTime} are plain math); the rest
 * needs the real invoking player, since {@code TriggerSystem}/{@code Capabilities} both require one.
 *
 * <p>Relies on {@code Flow.action(...)}'s own documented behavior (verified from source: {@code
 * Node#start} runs {@code onStart} synchronously, and {@code Action#onStart} runs its consumer
 * immediately — never deferred to the next server tick) to observe a fired trigger's effect
 * synchronously within this one command, with no need to wait a tick.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class TriggerSelfTestCommand {

    private TriggerSelfTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("trigger")
                        .then(Commands.literal("selftest").executes(TriggerSelfTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> failures = new ArrayList<>();

        testGeometryAndTime(failures);
        testOncePolicy(player, failures);
        testRepeatPolicy(player, failures);
        testPersistentOnce(player, failures);
        testDisabled(player, failures);

        if (failures.isEmpty()) {
            EngineLog.channel("Trigger").success("selftest: all checks passed").toChat(player);
        } else {
            String joined = String.join("; ", failures);
            EngineLog.channel("Trigger").error("selftest: {} failure(s): {}", failures.size(), joined).toChat(player);
        }
        return failures.isEmpty() ? 1 : 0;
    }

    /** Pure logic — no player/capability/network touched. */
    private static void testGeometryAndTime(List<String> failures) {
        Trigger radiusTrigger = Trigger.location("__selftest_radius__")
                .radius(new BlockPos(0, 64, 0), 5)
                .whenEntered()
                .run(Flow.action(ctx -> {
                }));
        checkTrue(failures, "location radius: inside", radiusTrigger.containsPosition(new Vec3(2, 64, 0)));
        checkTrue(failures, "location radius: outside", !radiusTrigger.containsPosition(new Vec3(20, 64, 0)));

        Trigger aabbTrigger = Trigger.location("__selftest_aabb__")
                .aabb(new BlockPos(0, 60, 0), new BlockPos(10, 70, 10))
                .whenEntered()
                .run(Flow.action(ctx -> {
                }));
        checkTrue(failures, "location aabb: inside", aabbTrigger.containsPosition(new Vec3(5, 65, 5)));
        checkTrue(failures, "location aabb: outside", !aabbTrigger.containsPosition(new Vec3(50, 65, 5)));

        Trigger timeTrigger = Trigger.time("__selftest_time__").at(13000).run(Flow.action(ctx -> {
        }));
        checkTrue(failures, "time: matches window start", timeTrigger.matchesTime(13000));
        checkTrue(failures, "time: matches within window", timeTrigger.matchesTime(13010));
        checkTrue(failures, "time: outside window", !timeTrigger.matchesTime(14000));

        Trigger rangeTrigger = Trigger.time("__selftest_range__").between(13000, 23000).run(Flow.action(ctx -> {
        }));
        checkTrue(failures, "time range: inside", rangeTrigger.matchesTime(18000));
        checkTrue(failures, "time range: outside", !rangeTrigger.matchesTime(6000));

        Trigger wrapTrigger = Trigger.time("__selftest_wrap__").between(23000, 1000).run(Flow.action(ctx -> {
        }));
        checkTrue(failures, "time range wrap: inside (late)", wrapTrigger.matchesTime(23500));
        checkTrue(failures, "time range wrap: inside (early)", wrapTrigger.matchesTime(500));
        checkTrue(failures, "time range wrap: outside", !wrapTrigger.matchesTime(12000));
    }

    /** §15.5 — ONCE: a second fire for the same player must not run the Flow again. */
    private static void testOncePolicy(ServerPlayer player, List<String> failures) {
        AtomicInteger fireCount = new AtomicInteger();
        Trigger trigger = counterTrigger("__selftest_once__").once().run(counterFlow(fireCount));

        TriggerSystem.fireForPlayer(trigger, player);
        TriggerSystem.fireForPlayer(trigger, player);

        checkTrue(failures, "once policy", fireCount.get() == 1);
    }

    /** §15.6 — REPEAT: firing again must run the Flow again. */
    private static void testRepeatPolicy(ServerPlayer player, List<String> failures) {
        AtomicInteger fireCount = new AtomicInteger();
        Trigger trigger = counterTrigger("__selftest_repeat__").repeat().run(counterFlow(fireCount));

        TriggerSystem.fireForPlayer(trigger, player);
        TriggerSystem.fireForPlayer(trigger, player);

        checkTrue(failures, "repeat policy", fireCount.get() == 2);
    }

    /** §15.7 — persistent ONCE: the capability round-trip a restart would rely on, exercised directly (this project has no way to actually restart the server from a command). */
    private static void testPersistentOnce(ServerPlayer player, List<String> failures) {
        ResourceLocation id = new ResourceLocation(StoryModEngine.MODID, "__selftest_persistent__");
        TriggerPlayerStateData data = Capabilities.get(player, ModTriggerCapabilities.PLAYER_STATE);
        if (data == null) {
            failures.add("persistent once: no TriggerPlayerStateData capability on this player");
            return;
        }
        data.fired.remove(id);

        AtomicInteger fireCount = new AtomicInteger();
        Trigger trigger = Trigger.location(id.getPath())
                .radius(BlockPos.ZERO, 1)
                .whenEntered()
                .once()
                .persistent()
                .run(counterFlow(fireCount));

        TriggerSystem.fireForPlayer(trigger, player);
        checkTrue(failures, "persistent once: first fire runs", fireCount.get() == 1);
        checkTrue(failures, "persistent once: capability now records it", data.fired.contains(id));

        TriggerSystem.fireForPlayer(trigger, player);
        checkTrue(failures, "persistent once: second fire (same session) blocked", fireCount.get() == 1);

        data.fired.remove(id);
    }

    /** §15.8 (bundled in) — a disabled trigger never fires, even via the manual bypass. */
    private static void testDisabled(ServerPlayer player, List<String> failures) {
        AtomicInteger fireCount = new AtomicInteger();
        Trigger trigger = counterTrigger("__selftest_disabled__").repeat().run(counterFlow(fireCount));
        trigger.setEnabled(false);

        TriggerSystem.fireForPlayer(trigger, player);

        checkTrue(failures, "disabled trigger does not fire", fireCount.get() == 0);
    }

    private static Trigger.Builder counterTrigger(String id) {
        return Trigger.location(id).radius(BlockPos.ZERO, 1).whenEntered();
    }

    private static Flow counterFlow(AtomicInteger fireCount) {
        return Flow.action(ctx -> fireCount.incrementAndGet());
    }

    private static void checkTrue(List<String> failures, String label, boolean condition) {
        if (!condition) {
            failures.add(label);
        }
    }
}
