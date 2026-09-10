package com.dimalab.storymodengine.common.scripting.command;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.scripting.registry.ZoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The engine's own {@code give}/{@code teleport}/{@code play_sound}/{@code spawn}/{@code set_block}
 * commands — plain {@code @StoryCommand}-annotated static methods, discovered and dispatched through
 * the exact same {@code StoryCommandDiscovery}/{@code StoryCommandRegistry} path a mod author's own
 * commands go through. Unqualified item/entity/block/sound ids default to the {@code minecraft}
 * namespace (see {@code ScriptingRegistryFacade#contentId}).
 */
public final class BuiltinStoryCommands {

    private BuiltinStoryCommands() {
    }

    @StoryCommand("give")
    public static void give(ServerPlayer player, String itemId, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(id(itemId));
        if (item == null) {
            EngineLog.channel("SME").warn("give: unknown item id '{}'", itemId);
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @StoryCommand("teleport")
    public static void teleport(ServerPlayer player, String zoneName) {
        ZoneRegistry.Zone zone = ZoneRegistry.get(zoneName);
        if (zone == null) {
            EngineLog.channel("SME").warn("teleport: unknown zone '{}' — register it via ZoneRegistry.register(...)", zoneName);
            return;
        }
        player.teleportTo(zone.center().x, zone.center().y, zone.center().z);
    }

    @StoryCommand("play_sound")
    public static void playSound(ServerPlayer player, String soundId) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id(soundId));
        if (sound == null) {
            EngineLog.channel("SME").warn("play_sound: unknown sound id '{}'", soundId);
            return;
        }
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @StoryCommand("spawn")
    public static void spawn(ServerPlayer player, String entityTypeId) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id(entityTypeId));
        if (type == null) {
            EngineLog.channel("SME").warn("spawn: unknown entity type id '{}'", entityTypeId);
            return;
        }
        type.spawn(player.serverLevel(), null, player, player.blockPosition(), MobSpawnType.COMMAND, false, false);
    }

    @StoryCommand("set_block")
    public static void setBlock(ServerPlayer player, String blockId, int x, int y, int z) {
        Block block = ForgeRegistries.BLOCKS.getValue(id(blockId));
        if (block == null) {
            EngineLog.channel("SME").warn("set_block: unknown block id '{}'", blockId);
            return;
        }
        player.level().setBlockAndUpdate(new BlockPos(x, y, z), block.defaultBlockState());
    }

    private static ResourceLocation id(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }
}
