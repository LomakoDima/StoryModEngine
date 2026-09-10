package com.dimalab.storymodengine.common.model.block;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Registration for the model block.
 *
 * <p>The block itself goes through {@link AutoContent} like any other content in this engine — that
 * also gets it a {@code BlockItem} and generated blockstate/model JSON for free, so it can be held
 * and placed like a normal block. Its {@code BlockEntityType}, however, has no {@code AutoContent}
 * route: {@code ContentTypeRegistry} has no binding for it, and a block entity type can't be
 * inferred from a field's type alone (it needs the block it attaches to). A plain {@code
 * DeferredRegister} is the honest way to register it rather than bending the content pipeline
 * around one case.
 */
public final class ModelBlocks {

    @AutoContent
    public static final Supplier<Block> MODEL_BLOCK = ContentHolder.of(ModelBlock::new);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StoryModEngine.MODID);

    public static final RegistryObject<BlockEntityType<ModelBlockEntity>> MODEL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("model_block", () ->
                    BlockEntityType.Builder.of(ModelBlockEntity::new, MODEL_BLOCK.get()).build(null));

    private ModelBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
