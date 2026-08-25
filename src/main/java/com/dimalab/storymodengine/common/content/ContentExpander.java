package com.dimalab.storymodengine.common.content;

/**
 * Registers additional content that a discovered {@code @AutoContent} field implies but doesn't
 * declare directly — e.g. a {@code Block} implying a {@code BlockItem}, or a {@code FluidType}
 * implying the still/flowing fluid pair, a placeable block, and a bucket. An expander runs once,
 * right after its primary content is registered, and only for that one field; whatever it
 * registers via {@link ExpansionContext#register} does not itself trigger further expansion.
 *
 * <p>This is the extension point new content categories use to pull in Forge-mandated companion
 * registrations without {@code @AutoContent} ever growing parameters, and without a sibling
 * annotation per content type — register one via
 * {@link ContentTypeRegistry#registerExpander(Class, ContentExpander)}.
 */
@FunctionalInterface
public interface ContentExpander<T> {

    void expand(ExpansionContext<T> context);
}
