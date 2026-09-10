package com.dimalab.storymodengine.common.shop.network;

/**
 * A small, self-contained network copy of one {@code ShopEntry} — kept separate from that type so
 * {@code ShopOpenPacket} doesn't need to depend on {@code common.shop.ShopDefinition}'s own types.
 */
public record ShopEntrySnapshot(String itemId, int price, int count) {
}
