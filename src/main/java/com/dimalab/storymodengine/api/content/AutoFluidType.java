package com.dimalab.storymodengine.api.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

/**
 * A {@link FluidType} that wires up its own client rendering by convention, the same way
 * {@code @AutoContent} blocks/items get their models from id-based paths: still/flowing textures
 * at {@code assets/<modid>/textures/block/<id>_still.png} and {@code <id>_flow.png} — the same
 * folder and naming vanilla uses for water and lava.
 *
 * <p>Unlike blockstates/models, Forge has no data-driven file for this in 1.20.1 — a
 * {@code FluidType} resolves its own render properties in code
 * ({@link FluidType#initializeClient}), lazily, the first time the client needs them. By then the
 * type is already registered, so this looks its own id up from {@link ForgeRegistries#FLUID_TYPES}
 * rather than needing it injected — a mod author who instantiates this class gets the convention
 * for free; one who extends {@link FluidType} directly is opting out of it.
 */
public class AutoFluidType extends FluidType {

    public AutoFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        ResourceLocation key = ForgeRegistries.FLUID_TYPES.get().getKey(this);
        String stillPath = key.getPath() + "_still";
        String flowPath = key.getPath() + "_flow";
        ResourceLocation still = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "block/" + stillPath);
        ResourceLocation flowing = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "block/" + flowPath);

        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowing;
            }
        });
    }
}
