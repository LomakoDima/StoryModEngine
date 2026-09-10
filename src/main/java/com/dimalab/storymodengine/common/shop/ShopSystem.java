package com.dimalab.storymodengine.common.shop;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.Network;
import com.dimalab.storymodengine.common.shop.network.ShopEntrySnapshot;
import com.dimalab.storymodengine.common.shop.network.ShopOpenPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side shop logic — deliberately stateless: unlike {@code dialogue.DialogueSystem}, there is
 * no per-player "active shop" to track, because a shop supports repeated purchases without closing
 * (pick another item, buy again), which doesn't fit dialogue's "pick one option, consume it, advance"
 * model. Every {@link #buy} call re-validates against the player's live inventory from scratch, so
 * there's nothing to leak or desync if the player disconnects mid-session.
 *
 * <p>Currency is a real, countable vanilla item — the same convention villager trading already uses
 * — not a new persisted virtual-balance capability; {@link net.minecraft.world.Container#countItem}
 * reads it, {@link ContainerHelper#clearOrCountMatchingItems} atomically deducts it (verified against
 * the decompiled Forge sources — the exact utility {@code Inventory.clearOrCountMatchingItems} itself
 * calls).
 */
public final class ShopSystem {

    private ShopSystem() {
    }

    public static void open(ServerPlayer player, String shopId) {
        ShopDefinition shop = ShopDefinitionRegistry.get(shopId);
        if (shop == null) {
            EngineLog.channel("Shop").warn("open_shop: no shop declared under the name '{}'", shopId);
            return;
        }
        sendSnapshot(player, shop);
    }

    public static void buy(ServerPlayer player, String shopId, int entryIndex, int quantity) {
        ShopDefinition shop = ShopDefinitionRegistry.get(shopId);
        if (shop == null) {
            EngineLog.channel("Shop").warn("ShopBuyPacket: no shop declared under the name '{}'", shopId);
            return;
        }
        if (entryIndex < 0 || entryIndex >= shop.entries().size() || quantity < 1) {
            EngineLog.channel("Shop").warn("ShopBuyPacket: entry {} / quantity {} out of range for shop '{}'", entryIndex, quantity, shopId);
            return;
        }
        ShopEntry entry = shop.entries().get(entryIndex);
        Item currency = resolveItem(shop.currencyItemId());
        Item reward = resolveItem(entry.itemId());
        if (currency == null || reward == null) {
            EngineLog.channel("Shop").warn("ShopBuyPacket: shop '{}' references an unknown item id", shopId);
            return;
        }

        int totalCost = entry.price() * quantity;
        int balance = player.getInventory().countItem(currency);
        if (balance < totalCost) {
            // Refresh rather than silently drop — the client's own balance may be stale (another
            // purchase, or the currency item was spent/dropped elsewhere) since this screen opened.
            sendSnapshot(player, shop);
            return;
        }

        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.getItem() == currency, totalCost, false);
        ItemStack rewardStack = new ItemStack(reward, entry.count() * quantity);
        if (!player.getInventory().add(rewardStack)) {
            player.drop(rewardStack, false);
        }
        sendSnapshot(player, shop);
    }

    private static void sendSnapshot(ServerPlayer player, ShopDefinition shop) {
        Item currency = resolveItem(shop.currencyItemId());
        int balance = currency == null ? 0 : player.getInventory().countItem(currency);
        List<ShopEntrySnapshot> snapshots = new ArrayList<>(shop.entries().size());
        for (ShopEntry entry : shop.entries()) {
            snapshots.add(new ShopEntrySnapshot(entry.itemId(), entry.price(), entry.count()));
        }
        Network.sendToPlayer(player, new ShopOpenPacket(shop.name(), shop.currencyItemId(), List.copyOf(snapshots), balance));
    }

    private static Item resolveItem(String rawId) {
        return ForgeRegistries.ITEMS.getValue(id(rawId));
    }

    /** Same "unqualified id defaults to minecraft" convention {@code BuiltinStoryCommands#id} already uses. */
    private static ResourceLocation id(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }
}
