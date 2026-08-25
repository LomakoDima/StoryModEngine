package com.dimalab.storymodengine.common.content;

import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * What a {@link ContentExpander} has available: the id and namespace already derived for the
 * field it's expanding, the primary content itself (already registered), and a way to register
 * more content alongside it.
 */
public interface ExpansionContext<T> {

    String modId();

    /** The id derived from the annotated field's name, e.g. {@code "ruby_fluid"}. */
    String id();

    /** The already-registered primary content this expansion is running for. */
    Supplier<T> primary();

    /**
     * Registers one more piece of content under {@code id}, routed to whichever registry
     * {@link ContentTypeRegistry} has bound for {@code type} (or an ancestor of it — same
     * hierarchy walk primary content resolution uses).
     */
    <C> RegistryObject<C> register(Class<C> type, String id, Supplier<C> factory);
}
