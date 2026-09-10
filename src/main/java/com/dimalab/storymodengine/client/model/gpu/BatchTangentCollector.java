package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.client.renderer.RenderType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Per-{@link RenderType} accumulator for {@code at_tangent} data on the CPU-immediate/BATCHING render
 * path — pure bookkeeping, no GL calls (see {@link BatchTangentBuffers} for the GL-owning half).
 *
 * <p>{@code ModelRenderer.emitVertex} calls {@link #submit} once per vertex it pushes into a real
 * {@code VertexConsumer}, in the same order, for every primitive drawn through {@code
 * ModelRenderTypes#entityTriangles} — including primitives with no tangent data at all, which push a
 * placeholder, so a texture shared by several primitives never desyncs the accumulated float count
 * from the real vertex count. {@code RenderTypeMixin} calls {@link #stage} right before {@code
 * BufferUploader.drawWithShader} actually flushes that {@link RenderType}'s buffer, handing the
 * accumulated data to {@link BatchTangentBuffers} only if the count matches what's about to be drawn.
 *
 * <p>Keyed by identity (an {@link IdentityHashMap}, matching {@code InstanceFlush}'s own {@code
 * BUFFERS} map): {@code RenderType.create(...)} returns a package-private {@code CompositeRenderType},
 * not a type this engine can {@code instanceof}-check, so "which accumulator" can only be resolved by
 * object identity against the exact instance {@code ModelRenderTypes} cached and handed to both the
 * emitting code and (via the flush) this class.
 *
 * <p><b>Load-bearing invariant, shared with {@link BatchTangentBuffers}:</b> this only produces correct
 * results because {@code ModelRenderTypes.create()} passes {@code sortOnUpload = false} — {@code
 * RenderType.end()} only reorders vertices when that flag is {@code true}. If it's ever flipped (e.g.
 * to fix transparency sorting on a translucent model), the order accumulated here silently stops
 * matching the order in the real buffer, with no error — every tangent would land on the wrong vertex.
 *
 * <p><b>{@link #submit} caps each bucket at {@link #MAX_VERTICES_PER_BUCKET}</b> — a real crash, not a
 * theoretical one: a session that ran fine for two minutes hit an {@code OutOfMemoryError} inside
 * {@code FloatList.add} during {@code ShadowRenderer.renderEntities} at world disconnect, meaning
 * {@link #stage} (which only runs when this {@link RenderType}'s buffer actually flushes) never fired
 * for that bucket for however long the leak had been running — most likely Iris's shadow pipeline
 * being torn down mid-render on disconnect, still pushing vertices via {@code emitVertex} but no longer
 * reaching its own buffer's {@code endBatch()}/{@code RenderType.end()} to drain them. This class can't
 * fix a third-party pipeline's teardown ordering, and shouldn't try to — but it can guarantee its own
 * accumulator never grows past one frame's worth of a single texture's vertices regardless of why a
 * drain was missed, trading a dropped draw's tangent data (logged once) for never crashing the game.
 */
public final class BatchTangentCollector {

    /**
     * Comfortably above any real single-texture, single-frame vertex count (vanilla's own buffer size
     * hint for this render type is 256, per {@code ModelRenderTypes.create}) while still bounding worst
     * case memory to a few hundred KB per bucket ({@code MAX_VERTICES_PER_BUCKET * 4 floats * 4 bytes}).
     */
    private static final int MAX_VERTICES_PER_BUCKET = 65_536;

    private static final Map<RenderType, FloatList> PENDING = new IdentityHashMap<>();

    private BatchTangentCollector() {
    }

    /**
     * Appends one vertex's tangent (xyz + w handedness). No-op when {@code type} is {@code null} — the
     * {@code ModelSelfTest} recording-consumer call sites have no real {@link RenderType} and no GL
     * context. If this bucket's own {@link #stage} hasn't run in so long it's grown past {@link
     * #MAX_VERTICES_PER_BUCKET}, the accumulated data is dropped (logged once) and accumulation starts
     * over — see this class's own doc for the real crash that makes this necessary.
     */
    public static void submit(RenderType type, float x, float y, float z, float w) {
        if (type == null) {
            return;
        }
        FloatList list = PENDING.computeIfAbsent(type, t -> new FloatList());
        if (list.vertexCount() >= MAX_VERTICES_PER_BUCKET) {
            warnOverflowOnce();
            list.clear();
        }
        list.add(x, y, z, w);
    }

    private static boolean warnedAboutOverflow;

    private static void warnOverflowOnce() {
        if (warnedAboutOverflow) {
            return;
        }
        warnedAboutOverflow = true;
        EngineLog.channel("Model").warn("BATCHING tangent accumulator for one texture exceeded {} "
                        + "vertices without ever flushing — dropping it instead of growing further. This "
                        + "previously caused an OutOfMemoryError (see BatchTangentCollector's own doc); "
                        + "if this fires during normal play rather than at world disconnect, something is "
                        + "preventing this RenderType's buffer from ever flushing.",
                MAX_VERTICES_PER_BUCKET);
    }

    /** Drops every pending bucket; called alongside {@code BatchTangentBuffers.clearCache()} on a model reload so stale {@link RenderType} keys from before the reload don't linger. */
    public static void clearCache() {
        PENDING.clear();
    }

    /**
     * Pops whatever was accumulated for {@code type} and, only if its vertex count agrees with {@code
     * expectedVertexCount} (the count of the buffer actually about to be drawn), hands it to {@link
     * BatchTangentBuffers#stagePending}. A mismatch — which should never happen on the render thread
     * under the ordering this design relies on — is logged once and dropped rather than guessed at,
     * degrading that one draw back to today's no-tangent behavior instead of misattributing data.
     *
     * <p>Left in place (via {@link Map#get}, not removed) rather than popped out of {@link #PENDING} —
     * an earlier version removed the entry here, which meant {@link #submit}'s {@code
     * computeIfAbsent} allocated and regrew a brand-new {@code float[64]} back up to size on the very
     * next frame's first vertex for this same, still-live {@link RenderType}, every single frame.
     * {@link FloatList#clear} resets the count without discarding the backing array, so the next
     * frame's accumulation reuses whatever capacity this one already grew to.
     */
    public static void stage(RenderType type, int expectedVertexCount) {
        FloatList pending = PENDING.get(type);
        if (pending == null || pending.vertexCount() == 0) {
            return;
        }
        if (pending.vertexCount() != expectedVertexCount) {
            warnMismatchOnce(pending.vertexCount(), expectedVertexCount);
            pending.clear();
            return;
        }
        BatchTangentBuffers.stagePending(pending.toArray(), expectedVertexCount);
        pending.clear();
    }

    private static boolean warnedAboutMismatch;

    private static void warnMismatchOnce(int accumulated, int expected) {
        if (warnedAboutMismatch) {
            return;
        }
        warnedAboutMismatch = true;
        EngineLog.channel("Model").warn("BATCHING tangent accumulator held {} vertices but {} were "
                        + "flushed for the same RenderType — dropping this draw's tangent data instead "
                        + "of guessing. This should never happen on the render thread; if it recurs, "
                        + "something is emitting vertices for this RenderType outside ModelRenderer.",
                accumulated, expected);
    }

    /** Growable {@code float[4]}-per-vertex list — avoids boxing and matches the grow-only-buffer idiom used elsewhere in this package (e.g. {@code InstanceBatchBuffers}'s scratch buffers). */
    private static final class FloatList {
        private float[] data = new float[64];
        private int size;

        void add(float x, float y, float z, float w) {
            if (size + 4 > data.length) {
                float[] grown = new float[Math.max(data.length * 2, size + 4)];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = x;
            data[size++] = y;
            data[size++] = z;
            data[size++] = w;
        }

        int vertexCount() {
            return size / 4;
        }

        /** Resets to empty without shrinking the backing array — the overflow path reuses it immediately. */
        void clear() {
            size = 0;
        }

        float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }
}
