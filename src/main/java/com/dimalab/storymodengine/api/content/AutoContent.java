package com.dimalab.storymodengine.api.content;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static Supplier<T>} field as registrable content. {@link ContentDiscovery} finds
 * every such field in the owning mod's jar via Forge's own annotation scan data and registers it
 * automatically — the id is derived from the field name, the target registry from {@code T}, and
 * the namespace from the mod that owns the field. Nothing is configured on the annotation itself;
 * everything the engine needs, it infers.
 *
 * <p>The field is read exactly once, to obtain the supplier Forge will invoke during
 * {@code RegisterEvent}; it is never written back to, so it may be {@code final} or not, whichever
 * reads better at the call site.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoContent {
}
