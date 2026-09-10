package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The GL-owning half of BATCHING-path tangent support (see {@link BatchTangentCollector} for the pure
 * data-accumulation half). One reusable, grow-only VBO — same idiom as {@code
 * InstanceBatchBuffers}'s scratch buffers — reused across every draw rather than allocated fresh, plus
 * a per-program {@code at_tangent} attribute-location cache (a linked program's own attribute layout
 * never changes, so a location is resolved once per program id and kept forever; a shaderpack reload
 * simply produces a new program id with its own fresh cache entry).
 *
 * <p><b>Why this needs a Mixin at all</b>: the BATCHING RenderType ({@code ModelRenderTypes
 * #entityTriangles}) is built on {@code DefaultVertexFormat.NEW_ENTITY} — the exact same format
 * singleton vanilla itself uses for its own entity-cutout-no-cull rendering, which means our triangle
 * geometry and vanilla's own mob rendering share the one lazily-created {@code VertexBuffer}/VAO that
 * format owns for its whole lifetime (confirmed by reading {@code VertexFormat}/{@code VertexBuffer}
 * directly). {@code VertexBufferMixin} calls {@link #bindPendingIfMatches} at the very head of {@code
 * VertexBuffer.draw()} (VAO already bound, shader already applied — both happen earlier in the same
 * call chain) and {@link #unbindIfBound} at its tail. Binding only inside that narrow window, and
 * unconditionally disabling again right after, is what keeps this from leaking an enabled {@code
 * at_tangent} attribute array into the very next unrelated vanilla entity drawn through the same VAO.
 *
 * <p>{@link #bindPendingIfMatches} additionally refuses to bind if the resolved attribute location
 * falls in {@code [0,6]} — the range {@code DefaultVertexFormat.NEW_ENTITY}'s own Position/Color/UV0/
 * UV1/UV2/Normal/Padding attributes occupy. That should never happen (Iris's own {@code
 * ProgramCreator} fixes {@code at_tangent} at location 13 for every program it links — see {@code
 * InstanceBatchBuffers}'s own doc — and vanilla's unpatched shader doesn't declare it at all, resolving
 * to {@code -1}), but a collision there would corrupt position/color/uv/normal for every entity sharing
 * the VAO, not just ours, so it's worth refusing loudly rather than trusting the assumption blindly.
 */
public final class BatchTangentBuffers {

    private static final int MIN_SAFE_LOCATION = 7;

    private static final Map<Integer, Integer> LOCATION_CACHE = new HashMap<>();

    private static int vbo = -1;
    private static int vboCapacityBytes;

    // Reused across calls instead of ByteBuffer.allocateDirect-ing fresh every uploadAndBind() —
    // the exact anti-pattern InstanceBatchBuffers.instanceDataScratch already avoids, here scaling
    // with how many BATCHING-path triangle draws happen per frame. Grown (never shrunk) on demand.
    private static FloatBuffer uploadScratch;

    private static float[] pendingData;
    private static int pendingVertexCount;

    private static boolean bound;

    private BatchTangentBuffers() {
    }

    /** Called by {@link BatchTangentCollector#stage} once a flush's vertex count is confirmed to match. */
    static void stagePending(float[] tangents, int vertexCount) {
        pendingData = tangents;
        pendingVertexCount = vertexCount;
    }

    /**
     * Called from {@code VertexBufferMixin} at the head of {@code VertexBuffer.draw()}. A true no-op
     * whenever nothing is staged — {@code draw()} is also called directly by vanilla for persistent
     * buffers (terrain, sky) that have nothing to do with this bridge.
     */
    public static void bindPendingIfMatches(int currentIndexCount) {
        if (pendingData == null) {
            return;
        }
        float[] data = pendingData;
        int vertexCount = pendingVertexCount;
        pendingData = null;
        pendingVertexCount = 0;

        // Mode.TRIANGLES has no vertex expansion (unlike QUADS' 4-&gt;6), so index count equals vertex
        // count for this render type. A mismatch here means the render-thread/ordering assumption this
        // whole bridge relies on was violated between staging and this draw — discard rather than bind
        // tangent data to an unrelated draw.
        if (currentIndexCount != vertexCount) {
            warnIndexMismatchOnce(currentIndexCount, vertexCount);
            return;
        }

        var shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }
        int programId = shader.getId();
        int location = LOCATION_CACHE.computeIfAbsent(programId, id -> GL20.glGetAttribLocation(id, "at_tangent"));
        if (location < 0) {
            return;
        }
        if (location < MIN_SAFE_LOCATION) {
            warnUnsafeLocationOnce(location);
            return;
        }

        uploadAndBind(data, location);
        bound = true;
    }

    /** Called from {@code VertexBufferMixin} at the tail of {@code VertexBuffer.draw()}. Safe to call unconditionally. */
    public static void unbindIfBound() {
        if (!bound) {
            return;
        }
        bound = false;
        GL20.glDisableVertexAttribArray(boundLocation);
    }

    private static int boundLocation = -1;

    private static void uploadAndBind(float[] data, int location) {
        if (vbo < 0) {
            vbo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int neededBytes = data.length * 4;
        if (uploadScratch == null || uploadScratch.capacity() < data.length) {
            uploadScratch = ByteBuffer.allocateDirect(neededBytes).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        FloatBuffer buffer = uploadScratch;
        buffer.clear();
        buffer.put(data).flip();
        if (neededBytes > vboCapacityBytes) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
            vboCapacityBytes = neededBytes;
        } else {
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, buffer);
        }
        GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(location);
        boundLocation = location;
    }

    private static boolean warnedAboutIndexMismatch;

    private static void warnIndexMismatchOnce(int currentIndexCount, int stagedVertexCount) {
        if (warnedAboutIndexMismatch) {
            return;
        }
        warnedAboutIndexMismatch = true;
        EngineLog.channel("Model").warn("BATCHING tangent bridge: staged {} vertices but the draw about "
                        + "to fire has {} indices — discarding instead of binding to an unrelated draw. "
                        + "This should never happen on the render thread.",
                stagedVertexCount, currentIndexCount);
    }

    private static boolean warnedAboutUnsafeLocation;

    private static void warnUnsafeLocationOnce(int location) {
        if (warnedAboutUnsafeLocation) {
            return;
        }
        warnedAboutUnsafeLocation = true;
        EngineLog.channel("Model").warn("BATCHING tangent bridge: 'at_tangent' resolved to location {}, "
                        + "which collides with DefaultVertexFormat.NEW_ENTITY's own attributes (0-6) — "
                        + "refusing to bind it, since that would corrupt position/color/uv/normal for "
                        + "every entity sharing this VAO, not just ours.", location);
    }

    /** Drops cached GL state; called when model textures are released so a reload doesn't keep a VBO bound to a freed context, mirroring {@code ModelRenderTypes.clearCache()}. */
    public static void clearCache() {
        if (vbo >= 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = -1;
        }
        vboCapacityBytes = 0;
        uploadScratch = null;
        pendingData = null;
        pendingVertexCount = 0;
        bound = false;
        boundLocation = -1;
        LOCATION_CACHE.clear();
    }
}
