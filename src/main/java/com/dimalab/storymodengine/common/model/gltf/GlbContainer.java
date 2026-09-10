package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Splits a {@code .glb} file's binary container into its JSON chunk (as text) and optional BIN chunk
 * (as raw bytes) — a 12-byte header (magic/version/total length) followed by one or more
 * length-prefixed, type-tagged chunks, per the glTF 2.0 binary container spec. A plain {@code .gltf}
 * file (JSON only, no container) never goes through this class — {@code GltfModelParser} sniffs the
 * first 4 bytes for the {@code "glTF"} magic to decide which path to take.
 */
public final class GlbContainer {

    private static final int MAGIC = 0x46546C67;
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;
    private static final int CHUNK_TYPE_BIN = 0x004E4942;

    private final String json;
    private final byte[] binChunk;

    private GlbContainer(String json, byte[] binChunk) {
        this.json = json;
        this.binChunk = binChunk;
    }

    public static boolean isGlb(byte[] bytes) {
        return bytes.length >= 4 && ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() == MAGIC;
    }

    public static GlbContainer parse(ResourceLocation source, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12 || buffer.getInt() != MAGIC) {
            throw new ModelFormatException(source, "not a GLB file (bad magic)");
        }
        int version = buffer.getInt();
        if (version != 2) {
            throw new ModelFormatException(source, "unsupported GLB version " + version + " (only glTF 2.0 is supported)");
        }
        int totalLength = buffer.getInt();
        if (totalLength > bytes.length) {
            throw new ModelFormatException(source, "GLB header declares " + totalLength + " bytes, file only has " + bytes.length);
        }

        String json = null;
        byte[] bin = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (buffer.remaining() < chunkLength) {
                throw new ModelFormatException(source, "GLB chunk declares " + chunkLength + " bytes but only " + buffer.remaining() + " remain");
            }
            byte[] chunkData = new byte[chunkLength];
            buffer.get(chunkData);
            if (chunkType == CHUNK_TYPE_JSON) {
                json = new String(chunkData, StandardCharsets.UTF_8);
            } else if (chunkType == CHUNK_TYPE_BIN) {
                bin = chunkData;
            }
            // Any other chunk type is forward-compatible padding/extension data — already consumed
            // above by advancing the buffer past its declared length, nothing further to do.
        }

        if (json == null) {
            throw new ModelFormatException(source, "GLB file has no JSON chunk");
        }
        return new GlbContainer(json, bin);
    }

    public String json() {
        return json;
    }

    public byte[] binChunk() {
        return binChunk;
    }
}
