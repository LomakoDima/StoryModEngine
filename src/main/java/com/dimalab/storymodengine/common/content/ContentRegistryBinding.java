package com.dimalab.storymodengine.common.content;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Knows how to obtain a {@link DeferredRegister} for one Forge 1.20.1 code-registration category.
 * {@code DeferredRegister} already unifies the actual registration and mod-event-bus wiring for
 * both kinds of registry Forge lets mods register into via code — this only captures which
 * factory overload, and which registry reference, a given content type needs:
 *
 * <ul>
 *   <li>{@link #forgeRegistry(IForgeRegistry)} — a registry Forge itself owns and exposes as an
 *       {@code IForgeRegistry} (items, blocks, ...).</li>
 *   <li>{@link #vanillaRegistry(ResourceKey)} — a vanilla registry Forge still allows mods to
 *       contribute to via its {@code ResourceKey} (creative mode tabs, and others that aren't
 *       wrapped as an {@code IForgeRegistry}).</li>
 * </ul>
 *
 * <p>Deliberately does <b>not</b> attempt to cover dynamic/datapack registries (biomes, worldgen
 * features, and the like): those are populated from data files via data generation
 * ({@code RegistrySetBuilder} / {@code BootstrapContext}), not code registration — a structurally
 * different pipeline that a future engine subsystem will need to add on its own terms rather than
 * being forced through this interface. See {@code ARCHITECTURE.md}.
 */
@FunctionalInterface
public interface ContentRegistryBinding<T> {

    DeferredRegister<T> createRegister(String modId);

    static <T> ContentRegistryBinding<T> forgeRegistry(IForgeRegistry<T> registry) {
        return modId -> DeferredRegister.create(registry, modId);
    }

    static <T> ContentRegistryBinding<T> vanillaRegistry(ResourceKey<? extends Registry<T>> key) {
        return modId -> DeferredRegister.create(key, modId);
    }
}
