package com.dimalab.storymodengine.common.network.discovery;

import com.dimalab.storymodengine.api.network.PacketDirection;
import com.dimalab.storymodengine.api.network.annotation.Packet;
import com.dimalab.storymodengine.common.network.registry.PacketRegistry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;

/**
 * Finds every {@code @Packet} record in one mod's own jar via Forge's annotation scan data — the
 * exact same {@code ModFileScanData} mechanism {@code ContentDiscovery} already uses for {@code
 * @AutoContent} fields, here filtered to {@link ElementType#TYPE} instead of {@code FIELD}. Runs
 * once per mod, synchronously, before {@code NetworkBootstrap} builds that mod's channel.
 */
public final class PacketDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketDiscovery.class);
    private static final org.objectweb.asm.Type PACKET = org.objectweb.asm.Type.getType(Packet.class);

    private PacketDiscovery() {
    }

    /** Scans {@code modId}'s own jar for {@code @Packet} records and builds a registry for them. */
    public static PacketRegistry run(String modId) {
        PacketRegistry registry = new PacketRegistry();
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        int discovered = 0;
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.TYPE || !PACKET.equals(data.annotationType())) {
                continue;
            }
            if (registerType(modId, data.clazz().getClassName(), registry)) {
                discovered++;
            }
        }

        LOGGER.info("[{}] @Packet discovered {} packet(s)", modId, discovered);
        return registry;
    }

    private static boolean registerType(String modId, String className, PacketRegistry registry) {
        Class<?> type;
        try {
            type = Class.forName(className, true, PacketDiscovery.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load @Packet type " + className, e);
        }

        if (!type.isRecord()) {
            LOGGER.warn("[{}] Skipping @Packet on {} — must be a record (data-only by design; "
                    + "put behavior in a PacketHandler#handle override instead)", modId, className);
            return false;
        }

        PacketDirection direction = PacketDirection.of(type);
        if (direction == null) {
            LOGGER.warn("[{}] Skipping @Packet on {} — implements both ServerboundPacket and "
                    + "ClientboundPacket, a contradiction", modId, className);
            return false;
        }

        registry.register(type.asSubclass(Object.class), direction);
        LOGGER.debug("[{}] @Packet registered '{}' ({})", modId, className, direction);
        return true;
    }
}
