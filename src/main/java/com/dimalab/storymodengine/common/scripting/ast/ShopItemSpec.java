package com.dimalab.storymodengine.common.scripting.ast;

/**
 * One {@code item "<id>" price <n> [count <n>]} line inside a {@code shop { }} declaration —
 * pure data, like {@link NpcAttributeSpec}. {@code count} is how many of the item one purchase
 * unit gives (defaults to 1 — most shop entries sell one at a time).
 */
public record ShopItemSpec(String itemId, int price, int count) {
}
