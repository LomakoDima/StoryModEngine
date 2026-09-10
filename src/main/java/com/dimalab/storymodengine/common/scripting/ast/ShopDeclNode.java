package com.dimalab.storymodengine.common.scripting.ast;

import java.util.List;

/**
 * {@code shop "<name>" { currency "<itemId>" item "<itemId>" price <n> [count <n>] ... }} — pure
 * data, like {@link NpcDeclNode}/{@code DialogueDeclNode}. Declaring one does not open anything for
 * anyone; a script opens it later by name (see {@code open_shop} in {@code
 * common.shop.ShopStoryCommands}), same "declared, then explicitly started" split as npc/dialogue.
 *
 * @param name           the script-facing identifier {@code open_shop}/the debug command resolves by
 * @param currencyItemId the vanilla item id spent on every purchase (e.g. {@code "minecraft:emerald"})
 * @param items          the catalog, in declaration order — the order a script wrote {@code item}
 *                       lines is the order the shop screen lists them in
 */
public record ShopDeclNode(SourcePos pos, String name, List<MetadataTag> tags, String currencyItemId,
                            List<ShopItemSpec> items) implements SmeNode {
}
