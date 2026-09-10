package com.dimalab.storymodengine.common.shop;

import java.util.List;

/**
 * What {@code shop "<name>" { ... }} declared — pure data, like {@code npc.NpcDefinition}. Declaring
 * one opens nothing for anyone; {@code open_shop} (see {@link ShopStoryCommands}) does that later,
 * by {@link #name}, same "declared, then explicitly started" split as npc/dialogue/quest.
 *
 * @param currencyItemId bare vanilla item id spent on every purchase — resolved lazily at open/buy
 *                        time, same convention {@link ShopEntry#itemId} follows
 * @param entries         the catalog, in declaration order
 */
public record ShopDefinition(String name, String currencyItemId, List<ShopEntry> entries) {
}
