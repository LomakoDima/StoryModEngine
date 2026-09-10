package com.dimalab.storymodengine.common.entity;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.custom.ModelEntity;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity types this engine registers. There are two, deliberately: a <b>character</b> and a
 * <b>prop</b>, both of which wear whatever glTF model they are given rather than having one compiled
 * in — so a new character or a new piece of scenery needs no Java at all.
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StoryModEngine.MODID);

    /**
     * A story character. The declared size is only a starting point — {@link NpcEntity} overrides
     * {@code getDimensions} to fit the model it is actually wearing, which can't be known at
     * registration time since one type serves every character.
     */
    public static final RegistryObject<EntityType<NpcEntity>> NPC =
            ENTITY_TYPES.register("npc", () -> EntityType.Builder.of(NpcEntity::new, MobCategory.CREATURE)
                    .sized(NpcEntity.DEFAULT_BODY_WIDTH, 1.95f)
                    .clientTrackingRange(10)
                    .build("npc"));

    /** A model as a solid, unmoving object — see {@code /sme prop}. */
    public static final RegistryObject<EntityType<ModelEntity>> MODEL =
            ENTITY_TYPES.register("model", () -> EntityType.Builder.<ModelEntity>of(ModelEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(16)
                    .build("model"));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    /** Without this {@code Mob#getAttributes()} throws the moment an NPC is created. */
    @Mod.EventBusSubscriber(modid = StoryModEngine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {

        private Attributes() {
        }

        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.NPC.get(), NpcEntity.createAttributes().build());
        }
    }
}
