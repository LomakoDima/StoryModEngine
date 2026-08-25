package com.dimalab.storymodengine.common.capabilities;

import com.dimalab.storymodengine.common.capabilities.registry.CapabilityDescriptor;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import java.util.function.Supplier;

/**
 * A {@code @Capability}-annotated field's declared construction recipe. Unlike {@code
 * content.ContentHolder<T>} (which resolves to a single shared registered instance — an {@code
 * Item}/{@code Block} is a singleton), a {@code CapabilityData<T>} field never becomes "the"
 * instance: every attached owner (every entity, every block entity, every level) gets its own
 * separate {@code T}, held inside that owner's {@code CapabilityStorage}. This handle is purely a
 * typed, discoverable factory — {@link Capabilities#get}/{@link Capabilities#sync} do the actual
 * per-owner lookup, using {@code T.class} (recovered from this field's own generic signature by
 * {@code CapabilityDiscovery}) as the key.
 *
 * <pre>{@code
 * @Capability(sync = true)
 * public static final CapabilityData<StoryPlayerData> STORY_DATA = CapabilityData.of(StoryPlayerData::new);
 * }</pre>
 */
public final class CapabilityData<T> {

    private final Supplier<T> factory;
    private volatile CapabilityDescriptor<T> descriptor;

    private CapabilityData(Supplier<T> factory) {
        this.factory = factory;
    }

    /** Wraps the construction recipe {@code @Capability} should register — called once per owner, per attachment. */
    public static <T> CapabilityData<T> of(Supplier<T> factory) {
        return new CapabilityData<>(factory);
    }

    /** The construction recipe — read by {@code CapabilityDiscovery}, not normally called directly by mod authors. */
    public Supplier<T> factory() {
        return factory;
    }

    /**
     * Called once by {@code CapabilityDiscovery} right after it resolves this field's descriptor
     * (id, data type, owner kind, sync flag). Not normally called by mod authors — it's what lets
     * {@link #get}/{@link #sync} below work without asking the caller to repeat {@code T.class}
     * (which this handle otherwise has no reified reference to, generics being erased).
     */
    public void bind(CapabilityDescriptor<T> descriptor) {
        this.descriptor = descriptor;
    }

    /** The resolved descriptor — only valid after {@code CapabilityDiscovery} has run. */
    public CapabilityDescriptor<T> descriptor() {
        CapabilityDescriptor<T> resolved = descriptor;
        if (resolved == null) {
            throw new IllegalStateException(
                    "Not yet discovered — was this field found by CapabilityDiscovery.run(...)?");
        }
        return resolved;
    }

    /** Same as {@code Capabilities.get(owner, thisFieldsDataType())} — see {@link Capabilities#get}. */
    public T get(ICapabilityProvider owner) {
        return Capabilities.get(owner, descriptor().dataType());
    }

    /** Same as {@code Capabilities.sync(owner, thisFieldsDataType())} — see {@link Capabilities#sync}. */
    public void sync(ICapabilityProvider owner) {
        Capabilities.sync(owner, descriptor().dataType());
    }

    /** Same as {@code Capabilities.markDirty(owner, thisFieldsDataType())} — see {@link Capabilities#markDirty}. */
    public void markDirty(ICapabilityProvider owner) {
        Capabilities.markDirty(owner, descriptor().dataType());
    }
}
