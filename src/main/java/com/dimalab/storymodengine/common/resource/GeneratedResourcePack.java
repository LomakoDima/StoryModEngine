package com.dimalab.storymodengine.common.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory {@link PackResources} whose contents are Java objects computed by
 * {@link AssetGenerator} (for a {@link PackType#CLIENT_RESOURCES} instance) or {@link
 * DataGenerator} (for a {@link PackType#SERVER_DATA} instance) — bytes held in a {@link Map}, not
 * files on disk. Nothing here is ever written to {@code src/main/resources}/{@code
 * src/main/generated}, and none of it depends on a prior {@code runData} run: {@link
 * ResourcePacks} builds a fresh instance for each pack type on every reload, straight from
 * whatever {@code ContentDiscovery} has registered by then.
 *
 * <p>One instance only ever answers for the single {@link PackType} it was built for — mirroring
 * how a real pack's {@code assets/} and {@code data/} folders are two independent things Minecraft
 * queries through the same {@link PackResources}, just never both from one generated map here,
 * since {@link AssetGenerator} and {@link DataGenerator} are asked separately and only when their
 * matching {@code AddPackFindersEvent} fires.
 */
final class GeneratedResourcePack implements PackResources {

    private final String packId;
    private final PackType packType;
    private final Map<ResourceLocation, byte[]> resources;

    GeneratedResourcePack(String packId, PackType packType, Map<ResourceLocation, byte[]> resources) {
        this.packId = packId;
        this.packType = packType;
        this.resources = resources;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != packType) {
            return null;
        }
        byte[] bytes = resources.get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        if (type != packType) {
            return;
        }
        resources.forEach((location, bytes) -> {
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path)) {
                resourceOutput.accept(location, () -> new ByteArrayInputStream(bytes));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != packType) {
            return Set.of();
        }
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation location : resources.keySet()) {
            namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return null;
    }

    @Override
    public String packId() {
        return packId;
    }

    @Override
    public void close() {
    }
}
