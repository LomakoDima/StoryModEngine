package com.dimalab.storymodengine.common;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/**
 * A mod author's own content class — plain Java, no engine boilerplate, no registry class, no
 * package the engine needs to be told about. {@link AutoContent} infers everything it needs from
 * the field itself: {@code RUBY} becomes id {@code ruby}, {@code Supplier<Item>} routes to
 * {@code ForgeRegistries.ITEMS}, and the namespace comes from whichever mod is discovering
 * content when {@code ContentDiscovery.run(...)} runs — here, {@code storymodengine}.
 *
 * <p>Fields are {@code public static final}, matching Forge's own {@code RegistryObject<T>}
 * convention — {@link ContentHolder} makes that possible without the engine ever needing to
 * reassign the field itself; see its Javadoc.
 */
public class ModItems {

    @AutoContent
    public static final Supplier<Item> BISMUTH = ContentHolder.of(() -> new Item(new Item.Properties()));

    @AutoContent
    public static final Supplier<Block> BISMUTH_BLOCK = ContentHolder.of(() -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .requiresCorrectToolForDrops()
            .strength(5.0f, 6.0f)));
}
