package com.dimalab.storymodengine.common.trigger.persistence;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.trigger.Trigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

/** The {@code persistent()} half of {@code TriggerFiredTracker} — read/write/dirty through the existing {@code capabilities} system only, exactly the {@code QuestProgressStore} idiom. */
public final class TriggerStateStore {

    private TriggerStateStore() {
    }

    public static boolean hasFired(Trigger trigger, ServerPlayer player) {
        TriggerPlayerStateData data = Capabilities.get(player, ModTriggerCapabilities.PLAYER_STATE);
        return data != null && data.fired.contains(trigger.id());
    }

    public static void markFired(Trigger trigger, ServerPlayer player) {
        TriggerPlayerStateData data = Capabilities.get(player, ModTriggerCapabilities.PLAYER_STATE);
        if (data == null) {
            return;
        }
        data.fired.add(trigger.id());
        Capabilities.markDirty(player, ModTriggerCapabilities.PLAYER_STATE);
    }

    public static boolean hasFiredGlobal(ResourceLocation triggerId) {
        Level overworld = overworld();
        if (overworld == null) {
            return false;
        }
        TriggerGlobalStateData data = Capabilities.get(overworld, ModTriggerCapabilities.GLOBAL_STATE);
        return data != null && data.fired.contains(triggerId);
    }

    public static void markFiredGlobal(ResourceLocation triggerId) {
        Level overworld = overworld();
        if (overworld == null) {
            return;
        }
        TriggerGlobalStateData data = Capabilities.get(overworld, ModTriggerCapabilities.GLOBAL_STATE);
        if (data == null) {
            return;
        }
        data.fired.add(triggerId);
        Capabilities.markDirty(overworld, ModTriggerCapabilities.GLOBAL_STATE);
    }

    private static Level overworld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.overworld();
    }
}
