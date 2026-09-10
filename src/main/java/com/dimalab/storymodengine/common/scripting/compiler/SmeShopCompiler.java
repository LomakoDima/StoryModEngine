package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.common.scripting.ast.ShopDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.ShopItemSpec;
import com.dimalab.storymodengine.common.shop.ShopDefinition;
import com.dimalab.storymodengine.common.shop.ShopEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code shop "<name>" { ... }} → {@link ShopDefinition} — named {@code SmeShopCompiler} for the
 * same reason {@code SmeNpcCompiler} is. Unlike {@code SmeNpcCompiler}, no {@code Flow} is involved
 * at all: a shop's catalog is pure data, nothing here executes when the shop opens.
 */
public final class SmeShopCompiler {

    private SmeShopCompiler() {
    }

    public static ShopDefinition compile(ShopDeclNode node) {
        List<ShopEntry> entries = new ArrayList<>();
        for (ShopItemSpec item : node.items()) {
            entries.add(new ShopEntry(item.itemId(), item.price(), item.count()));
        }
        return new ShopDefinition(node.name(), node.currencyItemId(), List.copyOf(entries));
    }
}
