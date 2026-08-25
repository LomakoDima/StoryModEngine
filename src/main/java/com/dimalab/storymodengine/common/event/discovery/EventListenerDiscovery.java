package com.dimalab.storymodengine.common.event.discovery;

import com.dimalab.storymodengine.common.event.EventBus;
import com.dimalab.storymodengine.api.event.SubscribeEvent;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds every {@code @SubscribeEvent} method in one mod's own jar via Forge's annotation scan data
 * — the same {@code ModFileScanData} mechanism {@code ContentDiscovery}/{@code PacketDiscovery}/
 * {@code CapabilityDiscovery} already use, here filtered to {@link ElementType#METHOD}.
 *
 * <p>Unlike those three, a {@code METHOD}-targeted {@code AnnotationData#memberName()} is not a
 * bare name — verified by disassembling Forge's own scanner ({@code ModMethodVisitor}): it is the
 * method's name and its bytecode descriptor concatenated with no separator (e.g.
 * {@code "onTest(Lcom/example/TestEvent;)V"}), needed to disambiguate overloads. This class only
 * needs the <em>owning class</em> from each match, not the specific method — the exact method,
 * its single parameter's type, staticness, and accessibility are all re-validated by {@link
 * EventBus#register}, the same path a manual {@code EventBus.register(SomeClass.class)} call goes
 * through — so the descriptor itself is never parsed here; one distinct owner class per match is
 * enough to hand off to {@code register}, which finds every {@code @SubscribeEvent} method on that
 * class (static ones bind immediately; instance ones are silently skipped here, since discovery has
 * no instance to bind them to — see {@code SubscribeEvent}'s Javadoc).
 */
public final class EventListenerDiscovery {

    private static final org.objectweb.asm.Type SUBSCRIBE_EVENT = org.objectweb.asm.Type.getType(SubscribeEvent.class);

    private EventListenerDiscovery() {
    }

    /** Scans {@code modId}'s own jar for {@code @SubscribeEvent} methods and registers their owning classes' static listeners on {@code bus}. */
    public static void run(String modId, EventBus bus) {
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        Set<String> ownerClassNames = new LinkedHashSet<>();
        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.METHOD || !SUBSCRIBE_EVENT.equals(data.annotationType())) {
                continue;
            }
            ownerClassNames.add(data.clazz().getClassName());
        }

        int discovered = 0;
        for (String className : ownerClassNames) {
            try {
                Class<?> owner = Class.forName(className, true, EventListenerDiscovery.class.getClassLoader());
                discovered += bus.register(owner);
            } catch (ClassNotFoundException e) {
                EngineLog.channel("Events").warn("[{}] Failed to load @SubscribeEvent owner {}: {}", modId, className, e.toString());
            }
        }

        EngineLog.channel("Events").info(
                "[{}] @SubscribeEvent discovered {} static listener(s) across {} class(es)",
                modId, discovered, ownerClassNames.size());
    }
}
