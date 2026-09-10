package com.dimalab.storymodengine.common.shop;

/**
 * One catalog line inside a {@link ShopDefinition} — the runtime counterpart of {@code
 * common.scripting.ast.ShopItemSpec}, kept as a separate type so the compiled shop system doesn't
 * depend on the scripting AST package.
 *
 * @param itemId a bare vanilla item id (e.g. {@code "minecraft:iron_sword"}), resolved lazily at
 *               open/buy time — same "declare as a string, resolve on use" convention {@code
 *               NpcDefinition#model} already follows
 * @param price  currency-item count spent per {@link #count} units purchased
 * @param count  how many of {@link #itemId} one purchase unit gives (defaults to 1 when a script
 *               doesn't say otherwise)
 */
public record ShopEntry(String itemId, int price, int count) {
}
