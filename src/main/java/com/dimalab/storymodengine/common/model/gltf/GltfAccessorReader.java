package com.dimalab.storymodengine.common.model.gltf;

import com.dimalab.storymodengine.api.model.ModelFormatException;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns one glTF accessor into a plain Java array — the only place in this importer that touches
 * raw bytes, component types, or byteStride/byteOffset bookkeeping. Handles every componentType the
 * spec defines (BYTE/UNSIGNED_BYTE/SHORT/UNSIGNED_SHORT/UNSIGNED_INT/FLOAT) and every accessor
 * {@code type} the spec defines (SCALAR/VEC2/VEC3/VEC4/MAT2/MAT3/MAT4), including normalized-integer
 * decoding per the spec's own formula.
 *
 * <p><b>Sparse accessors are fully supported</b> (verified against HollowEngine's own reader, which
 * takes the same approach): {@link #forEachElement} reads the base array first — or, when an
 * accessor omits its {@code bufferView} entirely, leaves every element at the spec-mandated zero,
 * since a fresh Java array already starts zero-filled — then {@link #applySparse} overwrites just
 * the overridden elements from the sparse value buffer. Both are exercised by {@code
 * ModelSelfTest#testSparseAccessor} and {@code #testSparseAccessorWithoutBase}.
 */
public final class GltfAccessorReader {

    private static final int BYTE = 5120;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int SHORT = 5122;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;
    private static final int FLOAT = 5126;

    private final GltfDocument document;
    private final GltfBufferResolver buffers;
    private final ResourceLocation source;

    public GltfAccessorReader(GltfDocument document, GltfBufferResolver buffers, ResourceLocation source) {
        this.document = document;
        this.buffers = buffers;
        this.source = source;
    }

    /** For SCALAR/VEC2/VEC3/VEC4 accessors (positions, normals, uv, weights) — normalized integers decode to [0,1]/[-1,1] floats per spec, FLOAT accessors pass through unchanged. Result is {@code componentCount * accessor.count} floats, tightly packed regardless of the source's own byteStride. */
    public float[] readFloats(int accessorIndex) {
        GltfDocument.GltfAccessor accessor = document.accessors.get(accessorIndex);
        int componentCount = componentCount(accessor.type);
        float[] out = new float[accessor.count * componentCount];
        forEachElement(accessor, componentCount, (i, view, offset) -> {
            for (int c = 0; c < componentCount; c++) {
                out[i * componentCount + c] = readComponentAsFloat(view, offset + c * componentSize(accessor.componentType), accessor.componentType, accessor.normalized);
            }
        });
        return out;
    }

    /** For SCALAR/VEC-of-integer accessors (mesh indices, JOINTS_0) — widened to {@code int}, never normalized (indices and joint indices are never normalized per spec). */
    public int[] readInts(int accessorIndex) {
        GltfDocument.GltfAccessor accessor = document.accessors.get(accessorIndex);
        int componentCount = componentCount(accessor.type);
        int[] out = new int[accessor.count * componentCount];
        forEachElement(accessor, componentCount, (i, view, offset) -> {
            for (int c = 0; c < componentCount; c++) {
                out[i * componentCount + c] = readComponentAsInt(view, offset + c * componentSize(accessor.componentType), accessor.componentType);
            }
        });
        return out;
    }

    /** MAT4 accessors — inverse bind matrices, in practice the only use. glTF stores matrices column-major, exactly what JOML's {@code Matrix4f.set(float...)} already expects — no transpose needed. */
    public Matrix4f[] readMatrices(int accessorIndex) {
        GltfDocument.GltfAccessor accessor = document.accessors.get(accessorIndex);
        if (!"MAT4".equals(accessor.type)) {
            throw new ModelFormatException(source, "accessor " + accessorIndex + " is not a MAT4 (was " + accessor.type + ")");
        }
        float[] flat = readFloats(accessorIndex);
        Matrix4f[] out = new Matrix4f[accessor.count];
        for (int i = 0; i < accessor.count; i++) {
            float[] m = new float[16];
            System.arraycopy(flat, i * 16, m, 0, 16);
            out[i] = new Matrix4f().set(m);
        }
        return out;
    }

    @FunctionalInterface
    private interface ElementReader {
        void read(int elementIndex, ByteBuffer view, int byteOffsetInView);
    }

    /**
     * Walks every element of an accessor, base values first, then any sparse overrides on top.
     *
     * <p>An accessor may legally have <b>no</b> {@code bufferView} at all: the spec says its base
     * values are then all zero, and only the sparse entries carry data. So a missing bufferView is a
     * valid zero-filled base, not the error an earlier version treated it as.
     */
    private void forEachElement(GltfDocument.GltfAccessor accessor, int componentCount, ElementReader reader) {
        int elementSize = componentCount * componentSize(accessor.componentType);

        if (accessor.bufferView != null) {
            GltfDocument.GltfBufferView bufferView = document.bufferViews.get(accessor.bufferView);
            byte[] bufferBytes = buffers.resolve(bufferView.buffer);
            int viewOffset = bufferView.byteOffset == null ? 0 : bufferView.byteOffset;
            int accessorOffset = accessor.byteOffset == null ? 0 : accessor.byteOffset;
            int stride = (bufferView.byteStride == null || bufferView.byteStride == 0) ? elementSize : bufferView.byteStride;
            // A stride smaller than the element it's meant to space out doesn't overrun the buffer
            // (still caught by the per-element bounds check below) — it silently aliases consecutive
            // elements in memory instead, which is a structural file error, not semantically-degenerate
            // data to sanitize around.
            if (stride < elementSize) {
                throw new ModelFormatException(source, "bufferView byteStride (" + stride
                        + ") is smaller than its own element size (" + elementSize + ")");
            }

            ByteBuffer buffer = ByteBuffer.wrap(bufferBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < accessor.count; i++) {
                int elementStart = viewOffset + accessorOffset + i * stride;
                if (elementStart + elementSize > bufferBytes.length) {
                    throw new ModelFormatException(source, "accessor reads past the end of its buffer (element " + i + ")");
                }
                reader.read(i, buffer, elementStart);
            }
        } else if (accessor.sparse == null) {
            throw new ModelFormatException(source, "accessor has neither a bufferView nor sparse data");
        }

        applySparse(accessor, elementSize, reader);
    }

    /**
     * Re-reads the elements a sparse accessor overrides, from the sparse value buffer, after the base
     * pass has written them. Overriding by re-invoking the same reader keeps every component-type and
     * normalization rule in one place instead of duplicating the decode for the sparse path.
     */
    private void applySparse(GltfDocument.GltfAccessor accessor, int elementSize, ElementReader reader) {
        GltfDocument.GltfSparse sparse = accessor.sparse;
        if (sparse == null || sparse.count <= 0) {
            return;
        }

        GltfDocument.GltfBufferView indexView = document.bufferViews.get(sparse.indices.bufferView);
        byte[] indexBytes = buffers.resolve(indexView.buffer);
        ByteBuffer indexBuffer = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
        int indexStart = (indexView.byteOffset == null ? 0 : indexView.byteOffset)
                + (sparse.indices.byteOffset == null ? 0 : sparse.indices.byteOffset);
        int indexSize = componentSize(sparse.indices.componentType);

        GltfDocument.GltfBufferView valueView = document.bufferViews.get(sparse.values.bufferView);
        byte[] valueBytes = buffers.resolve(valueView.buffer);
        ByteBuffer valueBuffer = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN);
        int valueStart = (valueView.byteOffset == null ? 0 : valueView.byteOffset)
                + (sparse.values.byteOffset == null ? 0 : sparse.values.byteOffset);

        for (int i = 0; i < sparse.count; i++) {
            int targetIndex = readComponentAsInt(indexBuffer, indexStart + i * indexSize, sparse.indices.componentType);
            if (targetIndex < 0 || targetIndex >= accessor.count) {
                throw new ModelFormatException(source, "sparse accessor overrides element " + targetIndex + ", outside its own count of " + accessor.count);
            }
            int elementStart = valueStart + i * elementSize;
            if (elementStart + elementSize > valueBytes.length) {
                throw new ModelFormatException(source, "sparse accessor reads past the end of its value buffer (entry " + i + ")");
            }
            reader.read(targetIndex, valueBuffer, elementStart);
        }
    }

    private static float readComponentAsFloat(ByteBuffer buffer, int byteOffset, int componentType, boolean normalized) {
        return switch (componentType) {
            case FLOAT -> buffer.getFloat(byteOffset);
            case BYTE -> normalized ? Math.max(buffer.get(byteOffset) / 127f, -1f) : buffer.get(byteOffset);
            case UNSIGNED_BYTE -> {
                int v = buffer.get(byteOffset) & 0xFF;
                yield normalized ? v / 255f : v;
            }
            case SHORT -> normalized ? Math.max(buffer.getShort(byteOffset) / 32767f, -1f) : buffer.getShort(byteOffset);
            case UNSIGNED_SHORT -> {
                int v = buffer.getShort(byteOffset) & 0xFFFF;
                yield normalized ? v / 65535f : v;
            }
            case UNSIGNED_INT -> (float) (buffer.getInt(byteOffset) & 0xFFFFFFFFL);
            default -> throw new IllegalStateException("unsupported componentType " + componentType);
        };
    }

    private static int readComponentAsInt(ByteBuffer buffer, int byteOffset, int componentType) {
        return switch (componentType) {
            case BYTE -> buffer.get(byteOffset);
            case UNSIGNED_BYTE -> buffer.get(byteOffset) & 0xFF;
            case SHORT -> buffer.getShort(byteOffset);
            case UNSIGNED_SHORT -> buffer.getShort(byteOffset) & 0xFFFF;
            case UNSIGNED_INT -> Math.toIntExact(buffer.getInt(byteOffset) & 0xFFFFFFFFL);
            case FLOAT -> (int) buffer.getFloat(byteOffset);
            default -> throw new IllegalStateException("unsupported componentType " + componentType);
        };
    }

    private static int componentSize(int componentType) {
        return switch (componentType) {
            case BYTE, UNSIGNED_BYTE -> 1;
            case SHORT, UNSIGNED_SHORT -> 2;
            case UNSIGNED_INT, FLOAT -> 4;
            default -> throw new IllegalStateException("unsupported componentType " + componentType);
        };
    }

    /**
     * Matches every accessor {@code type} the spec defines, not just the ones this importer's own
     * mesh/skin/animation attributes happen to use — {@code readFloats}/{@code readInts} then work on
     * any accessor a document contains, MAT2/MAT3 included, the same way HollowEngine's own accessor
     * layer sizes an element by component count regardless of whether anything downstream reads that
     * particular type yet.
     */
    private static int componentCount(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalStateException("unsupported accessor type " + type);
        };
    }
}
