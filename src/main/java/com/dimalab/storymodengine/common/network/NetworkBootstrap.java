package com.dimalab.storymodengine.common.network;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.network.discovery.PacketDiscovery;
import com.dimalab.storymodengine.common.network.registry.PacketDescriptor;
import com.dimalab.storymodengine.common.network.registry.PacketRegistry;
import com.dimalab.storymodengine.common.network.routing.PacketRouter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Wires one mod's discovered {@code @Packet} records into a real {@link SimpleChannel} — the only
 * place {@code SimpleChannel} is mentioned outside {@code net.minecraftforge.network} itself. Runs
 * synchronously from {@code EngineBootstrap.init(modEventBus)}, in the mod's own constructor,
 * because channel creation (like {@code DeferredRegister}) must happen before Forge's registration
 * phase locks (verified against {@code NetworkRegistry} source: {@code createInstance} throws once
 * {@code lock} is set) — there is no event to defer this to, nor does one exist for {@code
 * SimpleChannel} the way there is for content registration.
 *
 * <p>No {@code Dist.CLIENT} gate here, unlike {@code AutoParticleRegistration}: creating a channel
 * and registering messages are common-side Forge APIs that behave identically on a dedicated
 * server (verified against source — nothing in {@code NetworkRegistry}/{@code SimpleChannel}'s
 * registration path touches a client-only class).
 */
public final class NetworkBootstrap {

    private static final String PROTOCOL_VERSION = "1";

    private NetworkBootstrap() {
    }

    /** Call once, from {@code EngineBootstrap.init(modEventBus)} — same shape as {@code ContentDiscovery.run}/{@code ResourcePacks.register}. */
    public static void init(IEventBus modEventBus) {
        String modId = ModLoadingContext.get().getContainer().getModId();
        PacketRegistry registry = PacketDiscovery.run(modId);

        SimpleChannel channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(modId, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);

        for (PacketDescriptor<?> descriptor : registry.all()) {
            registerWithChannel(modId, channel, descriptor);
        }

        EngineLog.channel("Network").debug(
                "[{}] Registered {} packet(s) on channel {}:main", modId, registry.all().size(), modId);
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerWithChannel(String modId, SimpleChannel channel, PacketDescriptor<?> raw) {
        PacketDescriptor<T> descriptor = (PacketDescriptor<T>) raw;

        SimpleChannel.MessageBuilder<T> builder = descriptor.direction().forge()
                .map(direction -> channel.messageBuilder(descriptor.type(), descriptor.id(), direction))
                .orElseGet(() -> channel.messageBuilder(descriptor.type(), descriptor.id()));

        builder.encoder((msg, buf) -> descriptor.serializer().write(msg, buf))
                .decoder(buf -> PacketRouter.decode(modId, descriptor, buf))
                .consumerMainThread((msg, ctxSupplier) -> PacketRouter.route(modId, descriptor, msg, ctxSupplier))
                .add();

        Network.bind(descriptor.type(), channel);
        EngineLog.channel("Network").debug(
                "[{}] Registered packet {} (id {}, {})", modId, descriptor.type().getSimpleName(),
                descriptor.id(), descriptor.direction());
    }
}
