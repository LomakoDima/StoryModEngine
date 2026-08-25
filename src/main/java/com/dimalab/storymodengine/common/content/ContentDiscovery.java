package com.dimalab.storymodengine.common.content;

import com.dimalab.storymodengine.api.content.AutoContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Finds every {@link AutoContent}-annotated field in the calling mod's own jar and registers it
 * with Forge — no per-content-type registry class, and no list of packages to scan, required from
 * the mod author.
 *
 * <p>Discovery is driven by Forge's own annotation scan data ({@link ModFileScanData}), the same
 * mechanism Forge uses internally to locate {@code @Mod} classes and to power
 * {@code @AutoRegisterCapability}. It is available before the mod constructor runs, which is why
 * {@link #run(IEventBus)} can populate and wire up {@code DeferredRegister}s synchronously, ahead
 * of any {@code RegisterEvent} — exactly as a hand-written {@code DeferredRegister} setup would
 * need to.
 *
 * <p>An annotated field must be a {@code static} {@link ContentHolder} — declared as
 * {@code Supplier<T>} via {@link ContentHolder#of}, e.g.
 * {@code public static final Supplier<Item> RUBY = ContentHolder.of(() -> new Item(...));} — where
 * {@code T} is a type known to {@link ContentTypeRegistry}. The field itself is only ever read,
 * never written to (it can be genuinely {@code final}, matching Forge's own
 * {@code public static final RegistryObject<T>} convention); after registering the holder's
 * construction recipe, {@link #registerField} resolves the <em>holder object</em> in place via a
 * plain method call, not reflection. See {@link ContentHolder}'s Javadoc for why a raw
 * {@code Supplier<T>} that can be invoked more than once isn't safe for content like
 * {@code Item}/{@code Block} in 1.20.1.
 *
 * <p>Immediately after registering a field, any {@link ContentExpander}s
 * {@link ContentTypeRegistry#expandersFor} know about for that content type run — e.g. a
 * {@code BlockItem} for a {@code Block} (Forge does not create one automatically), or the full
 * still/flowing fluid + block + bucket set a {@code FluidType} needs.
 */
public final class ContentDiscovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentDiscovery.class);
    private static final org.objectweb.asm.Type AUTO_CONTENT = org.objectweb.asm.Type.getType(AutoContent.class);
    private static final List<ContentDescriptor<?>> DISCOVERED = new CopyOnWriteArrayList<>();

    private ContentDiscovery() {
    }

    /**
     * Scans the calling mod's own jar for {@link AutoContent} fields and registers them.
     * Call once, synchronously, from the mod's {@code @Mod} constructor.
     */
    public static void run(IEventBus modEventBus) {
        String modId = ModLoadingContext.get().getContainer().getModId();
        ModFileScanData scanData = ModList.get().getModFileById(modId).getFile().getScanResult();

        Map<ContentRegistryBinding<?>, DeferredRegister<?>> registers = new HashMap<>();
        int discovered = 0;

        for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
            if (data.targetType() != ElementType.FIELD || !AUTO_CONTENT.equals(data.annotationType())) {
                continue;
            }
            if (registerField(modId, data, registers)) {
                discovered++;
            }
        }

        registers.values().forEach(register -> register.register(modEventBus));

        LOGGER.info("[{}] @AutoContent discovered {} field(s) across {} registry(ies)",
                modId, discovered, registers.size());
    }

    /**
     * Every field discovered so far, across every mod that has called {@link #run(IEventBus)}.
     * Intended for future engine subsystems (e.g. generated resources) that need to act on the
     * same content the mod author declared, without re-scanning anything themselves.
     */
    public static List<ContentDescriptor<?>> getDiscovered() {
        return List.copyOf(DISCOVERED);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerField(String modId, ModFileScanData.AnnotationData data,
                                          Map<ContentRegistryBinding<?>, DeferredRegister<?>> registers) {
        String ownerName = data.clazz().getClassName();
        String fieldName = data.memberName();
        try {
            Class<?> ownerClass = Class.forName(ownerName, true, ContentDiscovery.class.getClassLoader());
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (!Modifier.isStatic(field.getModifiers()) || !Supplier.class.isAssignableFrom(field.getType())) {
                LOGGER.warn("[{}] Skipping @AutoContent on {}.{} — field must be a static Supplier<T>",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Type genericType = field.getGenericType();
            if (!(genericType instanceof ParameterizedType parameterized)) {
                LOGGER.warn("[{}] Skipping @AutoContent on {}.{} — Supplier is missing its type argument",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Object value = field.get(null);
            if (!(value instanceof ContentHolder holder)) {
                LOGGER.warn("[{}] Skipping @AutoContent on {}.{} — field must hold a ContentHolder"
                                + " (declare it as ContentHolder.of(() -> new ...(...)))",
                        modId, ownerClass.getSimpleName(), fieldName);
                return false;
            }

            Class contentType = (Class<?>) parameterized.getActualTypeArguments()[0];
            ContentRegistryBinding binding = ContentTypeRegistry.resolve(contentType);
            DeferredRegister register = registers.computeIfAbsent(binding, b -> b.createRegister(modId));

            String id = ContentIds.fromFieldName(fieldName);
            RegistryObject registryObject = register.register(id, holder.factory());
            holder.resolve(registryObject);

            DISCOVERED.add(new ContentDescriptor(ResourceLocation.fromNamespaceAndPath(modId, id), contentType, registryObject));

            LOGGER.debug("[{}] @AutoContent registered '{}' ({}) from {}.{}",
                    modId, id, contentType.getSimpleName(), ownerClass.getSimpleName(), fieldName);

            expandContent(modId, id, contentType, registryObject, registers);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to process @AutoContent on " + ownerName + "." + fieldName, e);
        }
    }

    /**
     * Runs every {@link ContentExpander} {@link ContentTypeRegistry} knows {@code contentType}
     * needs (e.g. a {@code BlockItem} for a {@code Block}), if any.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void expandContent(String modId, String id, Class contentType, Object registryObject,
                                       Map<ContentRegistryBinding<?>, DeferredRegister<?>> registers) {
        List<ContentExpander> expanders = (List) ContentTypeRegistry.expandersFor(contentType);
        if (expanders.isEmpty()) {
            return;
        }

        Supplier primary = (Supplier) registryObject;
        ExpansionContext context = new ExpansionContextImpl(modId, id, primary, registers);
        for (ContentExpander expander : expanders) {
            expander.expand(context);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class ExpansionContextImpl implements ExpansionContext {
        private final String modId;
        private final String id;
        private final Supplier primary;
        private final Map<ContentRegistryBinding<?>, DeferredRegister<?>> registers;

        ExpansionContextImpl(String modId, String id, Supplier primary,
                              Map<ContentRegistryBinding<?>, DeferredRegister<?>> registers) {
            this.modId = modId;
            this.id = id;
            this.primary = primary;
            this.registers = registers;
        }

        @Override
        public String modId() {
            return modId;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Supplier primary() {
            return primary;
        }

        @Override
        public RegistryObject register(Class type, String id, Supplier factory) {
            ContentRegistryBinding binding = ContentTypeRegistry.resolve(type);
            DeferredRegister register = registers.computeIfAbsent(binding, b -> ((ContentRegistryBinding) b).createRegister(modId));
            RegistryObject registryObject = register.register(id, factory);

            DISCOVERED.add(new ContentDescriptor(ResourceLocation.fromNamespaceAndPath(modId, id), type, registryObject));
            LOGGER.debug("[{}] @AutoContent registered expansion '{}' ({})", modId, id, type.getSimpleName());
            return registryObject;
        }
    }
}
