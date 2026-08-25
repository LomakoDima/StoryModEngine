package com.dimalab.storymodengine.common.content;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContentTypeRegistry {

    private static final Map<Class<?>, ContentRegistryBinding<?>> BINDINGS = new LinkedHashMap<>();
    private static final Map<Class<?>, List<ContentExpander<?>>> EXPANDERS = new LinkedHashMap<>();

    static {
        register(Item.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.ITEMS));
        register(Block.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.BLOCKS));
        register(MobEffect.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.MOB_EFFECTS));
        register(Fluid.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.FLUIDS));
        register(FluidType.class, ContentRegistryBinding.vanillaRegistry(ForgeRegistries.Keys.FLUID_TYPES));
        register(PaintingVariant.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.PAINTING_VARIANTS));
        registerRaw(ParticleType.class, ContentRegistryBinding.forgeRegistry(ForgeRegistries.PARTICLE_TYPES));

        registerExpander(Block.class, ctx ->
                ctx.register(BlockItem.class, ctx.id(), () -> new BlockItem(ctx.primary().get(), new Item.Properties())));

        registerExpander(FluidType.class, new FluidContentExpander());
    }

    private ContentTypeRegistry() {
    }

    public static <T> void register(Class<T> type, ContentRegistryBinding<T> binding) {
        BINDINGS.put(type, binding);
    }

    /**
     * Same as {@link #register}, for a registry keyed on a generic type (e.g. {@code
     * ParticleType<?>}) whose raw {@code Class} literal (what {@link ContentDiscovery} actually has
     * to work with — a field's runtime type argument, read via reflection, is erased) can never
     * satisfy {@code register}'s {@code Class<T>}/{@code ContentRegistryBinding<T>} pairing at
     * compile time: {@code ParticleType.class} is {@code Class<ParticleType>}, never {@code
     * Class<ParticleType<?>>}. The mismatch is inherent to raw class literals, not fixable by
     * calling this differently — hence a dedicated, deliberately unchecked entry point rather than
     * a workaround at each call site.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerRaw(Class type, ContentRegistryBinding binding) {
        register(type, binding);
    }

    public static <T> void registerExpander(Class<T> type, ContentExpander<T> expander) {
        EXPANDERS.computeIfAbsent(type, t -> new ArrayList<>()).add(expander);
    }

    static ContentRegistryBinding<?> resolve(Class<?> contentType) {
        for (Class<?> type = contentType; type != null; type = type.getSuperclass()) {
            ContentRegistryBinding<?> binding = BINDINGS.get(type);
            if (binding != null) return binding;
        }
        throw new IllegalArgumentException(
                "No registration strategy known for content type " + contentType.getName()
                        + " — register one via ContentTypeRegistry.register(...)");
    }

    @SuppressWarnings("unchecked")
    static <T> List<ContentExpander<T>> expandersFor(Class<?> contentType) {
        List<ContentExpander<T>> found = new ArrayList<>();
        for (Class<?> type = contentType; type != null; type = type.getSuperclass()) {
            List<ContentExpander<?>> expanders = EXPANDERS.get(type);
            if (expanders != null) {
                for (ContentExpander<?> expander : expanders) {
                    found.add((ContentExpander<T>) expander);
                }
            }
        }
        return found;
    }
}
