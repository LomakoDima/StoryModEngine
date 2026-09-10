package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves each glTF "buffer" entry's raw bytes uniformly, regardless of which of the three ways
 * glTF allows a buffer to supply its data: a GLB file's own embedded BIN chunk (buffer.uri absent),
 * a base64 {@code data:} URI (common in single-file {@code .gltf} exports), or an external file
 * referenced by a relative path next to the {@code .gltf} (read through the same {@link
 * ResourceManager} the model itself was loaded from, so it works packaged inside any resource pack,
 * not just the filesystem). Resolved once per buffer index and cached — a model with several
 * accessors reading the same buffer only decodes/reads it once. Also the one place image bytes
 * referenced by {@code uri} are read (see {@link #readSibling}), reused by {@link GltfMaterialImporter}.
 */
public final class GltfBufferResolver {

    private final GltfDocument document;
    private final byte[] glbBinChunk;
    private final ResourceManager resourceManager;
    private final ResourceLocation modelLocation;
    private final Map<Integer, byte[]> cache = new HashMap<>();

    public GltfBufferResolver(GltfDocument document, byte[] glbBinChunk, ResourceManager resourceManager, ResourceLocation modelLocation) {
        this.document = document;
        this.glbBinChunk = glbBinChunk;
        this.resourceManager = resourceManager;
        this.modelLocation = modelLocation;
    }

    public byte[] resolve(int bufferIndex) {
        return cache.computeIfAbsent(bufferIndex, this::resolveUncached);
    }

    private byte[] resolveUncached(int bufferIndex) {
        GltfDocument.GltfBuffer buffer = document.buffers.get(bufferIndex);
        if (buffer.uri == null) {
            if (glbBinChunk == null) {
                throw new ModelFormatException(modelLocation, "buffer " + bufferIndex + " has no uri and this is not a GLB file with a BIN chunk");
            }
            return glbBinChunk;
        }
        if (buffer.uri.startsWith("data:")) {
            return decodeDataUri(buffer.uri);
        }
        return readSibling(buffer.uri);
    }

    /**
     * Reads a file referenced by a relative path next to the model — used both for external buffers
     * and external images. Only base64 {@code data:} URIs are supported (see {@link #decodeDataUri});
     * a percent-encoded or otherwise non-base64 data URI is rejected with a clear message rather than
     * silently misdecoded.
     *
     * <p>A {@code null} {@link ResourceManager} falls back to reading the file straight off the
     * classpath. That's what lets the <b>server</b> parse a model at all: models live under {@code
     * assets/}, which a server's own {@code ResourceManager} never exposes, but the mod jar itself is
     * present on both sides — so the bytes are reachable, just not through the resource pipeline. The
     * server needs geometry (and only geometry) to derive authoritative collision; see {@code
     * physics.ModelPhysics}.
     */
    public byte[] readSibling(String relativePath) {
        ResourceLocation siblingLocation = siblingOf(modelLocation, relativePath);
        // Checked before the resource pack / classpath on either side — see ExternalModelIO's own
        // doc for why (an override folder next to the game, not inside the mod jar).
        byte[] external = ExternalModelIO.read(siblingLocation);
        if (external != null) {
            return external;
        }
        if (resourceManager == null) {
            return readFromClasspath(siblingLocation, relativePath);
        }
        try (InputStream in = resourceManager.open(siblingLocation)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ModelFormatException(modelLocation, "could not read referenced file '" + relativePath + "' (looked for " + siblingLocation + ")", e);
        }
    }

    private byte[] readFromClasspath(ResourceLocation siblingLocation, String relativePath) {
        String path = "/assets/" + siblingLocation.getNamespace() + "/" + siblingLocation.getPath();
        try (InputStream in = GltfBufferResolver.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new ModelFormatException(modelLocation, "could not read referenced file '" + relativePath + "' from the classpath (looked for " + path + ")");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ModelFormatException(modelLocation, "could not read referenced file '" + relativePath + "' from the classpath", e);
        }
    }

    private byte[] decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0 || !uri.substring(0, comma).contains(";base64")) {
            throw new ModelFormatException(modelLocation, "unsupported data URI (only base64 is supported): " + uri.substring(0, Math.min(uri.length(), 40)));
        }
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    /** Resolves a relative path (e.g. "textures/skin.png", or "../shared/skin.png") against the model's own resource location's directory, the same way a browser resolves a relative URL against a document's own path. */
    public static ResourceLocation siblingOf(ResourceLocation modelLocation, String relativePath) {
        String path = modelLocation.getPath();
        int lastSlash = path.lastIndexOf('/');
        String directory = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "";
        return new ResourceLocation(modelLocation.getNamespace(), normalize(directory + relativePath));
    }

    private static String normalize(String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..") && !segments.isEmpty()) {
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return String.join("/", segments);
    }
}
