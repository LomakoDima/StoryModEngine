package com.dimalab.storymodengine.common.content;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Expands a discovered {@link FluidType} into the objects Forge actually needs for a usable
 * fluid — there is no single-object "fluid" registration in Forge 1.20.1:
 *
 * <ul>
 *   <li>the still {@link Fluid} — id {@code <id>}</li>
 *   <li>the flowing {@link Fluid} — id {@code flowing_<id>}, matching vanilla's own
 *       {@code water}/{@code flowing_water} naming</li>
 *   <li>a {@link LiquidBlock} so it can exist in the world — id {@code <id>}, same id as the
 *       still fluid but a different registry ({@code ForgeRegistries.BLOCKS})</li>
 *   <li>a {@link BucketItem} so players can carry it — id {@code <id>_bucket}</li>
 * </ul>
 *
 * <p>All four reference each other lazily (a {@code Source} fluid's properties need a supplier
 * for the not-yet-registered {@code Flowing} fluid and vice versa), so each is registered behind
 * a mutable forward reference filled in the moment it's created — the same shape Forge's own
 * {@code ForgeFlowingFluid.Properties} is designed around.
 *
 * <p>Client rendering (still/flowing textures) is deliberately not handled here — see
 * {@link AutoFluidType}, which resolves it lazily from the fluid type's own registered id instead
 * of needing it threaded through registration.
 */
final class FluidContentExpander implements ContentExpander<FluidType> {

    @Override
    public void expand(ExpansionContext<FluidType> ctx) {
        String id = ctx.id();

        RegistryObject<ForgeFlowingFluid.Source>[] stillRef = newRef();
        RegistryObject<ForgeFlowingFluid.Flowing>[] flowingRef = newRef();
        RegistryObject<LiquidBlock>[] blockRef = newRef();
        RegistryObject<BucketItem>[] bucketRef = newRef();

        ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(
                ctx.primary(),
                () -> stillRef[0].get(),
                () -> flowingRef[0].get())
                .block(() -> blockRef[0].get())
                .bucket(() -> bucketRef[0].get());

        stillRef[0] = ctx.register(ForgeFlowingFluid.Source.class, id,
                () -> new ForgeFlowingFluid.Source(properties));
        flowingRef[0] = ctx.register(ForgeFlowingFluid.Flowing.class, "flowing_" + id,
                () -> new ForgeFlowingFluid.Flowing(properties));

        Supplier<ForgeFlowingFluid.Source> stillSupplier = () -> stillRef[0].get();
        blockRef[0] = ctx.register(LiquidBlock.class, id,
                () -> new LiquidBlock(stillSupplier, BlockBehaviour.Properties.copy(Blocks.WATER)));
        bucketRef[0] = ctx.register(BucketItem.class, id + "_bucket",
                () -> new BucketItem(stillSupplier, new Item.Properties().stacksTo(1)));
    }

    @SuppressWarnings("unchecked")
    private static <T> RegistryObject<T>[] newRef() {
        return new RegistryObject[1];
    }
}
