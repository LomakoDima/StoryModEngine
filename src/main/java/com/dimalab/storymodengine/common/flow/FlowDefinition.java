package com.dimalab.storymodengine.common.flow;

import net.minecraft.resources.ResourceLocation;

/**
 * A named, registerable {@link Flow} — the task's own {@code FlowDefinition} concept, made concrete
 * as a thin pairing rather than a rewrite of {@link Flow} itself. {@code Flow} already *is* an
 * immutable definition (a factory of {@code Node} factories, never a shared mutable instance); the
 * only thing it lacked was a stable identity to register/persist/resume by, which previously lived
 * as a separate {@code ResourceLocation} parameter threaded alongside a bare {@code Flow} wherever
 * one was needed. This record just gives that pairing a name — nothing about {@code Flow}'s own API
 * changed to make room for it.
 */
public record FlowDefinition(ResourceLocation id, Flow graph) {
}
