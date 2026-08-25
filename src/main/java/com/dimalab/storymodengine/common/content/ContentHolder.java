package com.dimalab.storymodengine.common.content;

import java.util.function.Supplier;

/**
 * Holds an {@link AutoContent} field's construction recipe, then — once {@link ContentDiscovery}
 * registers it — the resolved, registered instance. Declare a field as
 * {@code public static final Supplier<Item> RUBY = ContentHolder.of(() -> new Item(...));}: the
 * field itself never changes (it may be {@code final}, matching Forge's own convention for
 * {@code RegistryObject<T>} fields), only this holder's internal state does, through a plain
 * method call — never reflection.
 *
 * <p>This exists because of a real Forge/vanilla constraint, not a style preference: in 1.20.1,
 * {@code Item} and {@code Block} constructors register an intrusive holder with their registry,
 * which only works while that registry is unfrozen (i.e. during {@code RegisterEvent}). Calling
 * the raw construction recipe again later — e.g. because some other code called
 * {@code RUBY.get()} to reference the registered item — constructs a second instance and crashes
 * with {@code IllegalStateException: Registry is already frozen}. Routing every read through one
 * holder that resolves to the registered instance exactly once avoids that entirely.
 */
public final class ContentHolder<T> implements Supplier<T> {

    private final Supplier<T> factory;
    private volatile Supplier<T> resolved;

    private ContentHolder(Supplier<T> factory) {
        this.factory = factory;
    }

    /** Wraps the construction recipe {@link AutoContent} should register. */
    public static <T> ContentHolder<T> of(Supplier<T> factory) {
        return new ContentHolder<>(factory);
    }

    @Override
    public T get() {
        Supplier<T> current = resolved;
        if (current == null) {
            throw new IllegalStateException(
                    "Content not yet registered — was this field discovered by ContentDiscovery.run(...)?");
        }
        return current.get();
    }

    Supplier<T> factory() {
        return factory;
    }

    void resolve(Supplier<T> resolvedSupplier) {
        this.resolved = resolvedSupplier;
    }
}
