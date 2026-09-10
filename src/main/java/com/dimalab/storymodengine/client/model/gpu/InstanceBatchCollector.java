package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.client.model.RenderFrameClock;
import com.dimalab.storymodengine.common.model.Primitive;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One frame's worth of "draw this unskinned primitive here" requests, collected across every
 * entity {@code ModelRenderer} draws this frame and flushed together by {@link InstanceFlush} —
 * the cross-entity aggregation real instancing needs, since a single entity's own {@code render()}
 * call has no way to know what other entities sharing its model will also draw this frame.
 *
 * <p>Grouped by {@link Primitive} identity: two entities wearing the same {@code ModelDefinition}
 * share the exact same {@code Primitive} objects (a definition is parsed once and reused), so
 * grouping by that identity is exactly "instances of the same mesh" — matching how HollowEngine
 * groups by mesh identity (their {@code PipelineRenderer} reference) for the same reason.
 *
 * <p><b>Flattened directly into arrays, not a list of small objects.</b> An earlier version stored
 * one {@code Submission} record (itself a copied {@code Matrix4f} + copied {@code Matrix3f}) per
 * submission — three heap allocations per entity per frame, scaling with exactly the entity count
 * instancing exists to make cheap. {@link InstanceData#add} writes straight into a growable float
 * array instead, via JOML's {@code Matrix4f}/{@code Matrix3f} {@code get(float[], int)} overloads —
 * no intermediate matrix object at all. The copy itself is still required (the caller's own
 * {@code modelView}/{@code normalMatrix} are {@code ModelRenderer}'s reused scratch fields, mutated
 * again for the very next entity before this frame's batch is drained), it just no longer needs a
 * home of its own beyond the accumulator's own array.
 *
 * <p><b>Entries persist across frames instead of being thrown away</b> — {@link #SUBMISSIONS}/{@link
 * #TEXTURES} are never structurally cleared except by {@link #clearCache()} on a model reload. Each
 * {@link InstanceData} instead recognizes a new frame itself (see {@link InstanceData#add}'s own
 * {@code frame}-stamp check) and resets its own count lazily, on the first write of that new frame —
 * <b>not</b> via an explicit end-of-frame reset call. An earlier version called {@code reset()} on
 * every entry inside {@link #drainAndClear()} right after building this frame's {@link Batch}es — but
 * a {@code Batch} holds the exact same mutable {@link InstanceData} object, not a copy, so that reset
 * zeroed the very count {@link InstanceFlush} was about to read moments later: every instanced draw
 * that frame ran with {@code count=0}, rendering nothing.
 *
 * <p><b>The lazy-reset-on-write design has its own failure mode, also fixed here.</b> If every entity
 * sharing a primitive despawns, {@link InstanceData#add} simply never runs again for that entry — with
 * nothing to reset it, {@code count} would sit frozen at whatever it last was, and {@link
 * #drainAndClear()} would keep re-including it forever, drawing "ghost" copies of despawned entities at
 * their last known transforms. {@link #drainAndClear()} therefore also compares {@link
 * InstanceData#frame()} against the current frame before trusting {@link InstanceData#count()} at all —
 * a read-only check, so it carries none of the mutation risk the removed {@code reset()}-on-drain call
 * had.
 */
public final class InstanceBatchCollector {

    /** One primitive's worth of this frame's per-instance data, in the exact byte layout {@link InstanceBatchBuffers#draw} uploads. */
    public static final class InstanceData {
        private static final int FLOATS_PER_INSTANCE = 16 + 9; // mat4 (column-major) + mat3 (column-major), contiguous

        private float[] matrixData = new float[FLOATS_PER_INSTANCE * 8];
        private int[] overlayLight = new int[2 * 8];
        private int count;
        /** The {@link RenderFrameClock} frame this instance's data belongs to — see {@link #add}. */
        private long frame = -1;

        /**
         * Starts a fresh count for {@code currentFrame} if this is the first write since the last one
         * (lazy reset — see this class's own doc for why an explicit end-of-frame reset call is unsafe
         * here), then appends one instance. Old data past whatever {@link #count} was reset to is never
         * cleared, only overwritten as new instances are added — safe, since nothing ever reads past
         * {@link #count}.
         */
        private void add(long currentFrame, Matrix4f modelView, Matrix3f normalMatrix, int overlay, int light) {
            if (currentFrame != frame) {
                frame = currentFrame;
                count = 0;
            }
            ensureCapacity(count + 1);
            int offset = count * FLOATS_PER_INSTANCE;
            modelView.get(matrixData, offset);
            normalMatrix.get(matrixData, offset + 16);
            overlayLight[count * 2] = overlay;
            overlayLight[count * 2 + 1] = light;
            count++;
        }

        private void ensureCapacity(int needed) {
            if (needed * FLOATS_PER_INSTANCE <= matrixData.length) {
                return;
            }
            int newCount = Math.max(needed, count * 2);
            matrixData = Arrays.copyOf(matrixData, newCount * FLOATS_PER_INSTANCE);
            overlayLight = Arrays.copyOf(overlayLight, newCount * 2);
        }

        /**
         * {@code count()} is only meaningful for the frame it was last written on — see {@link
         * #drainAndClear()}, which checks this against {@link RenderFrameClock#currentFrame()} before
         * trusting {@link #count()} at all. Once nothing submits to this entry any more (every entity
         * wearing this primitive despawned or left view), {@link #add} simply never runs again, so
         * {@code count} would otherwise sit frozen at whatever it last was — a real bug this shipped
         * with once: every entity that had ever shared this primitive kept "drawing" at its last known
         * transforms forever, visible as duplicate/ghost geometry surviving a {@code /sme npc clear}.
         */
        long frame() {
            return frame;
        }

        public int count() {
            return count;
        }

        public float[] matrixData() {
            return matrixData;
        }

        public int[] overlayLight() {
            return overlayLight;
        }
    }

    /**
     * One primitive's worth of this frame's work, as handed to {@link InstanceFlush}. Bundles the
     * texture together with its data rather than making the caller look it up separately after
     * draining — a separate {@code textureFor(primitive)} call used to exist for that, but it read a
     * {@code TEXTURES} map that {@link #drainAndClear()} had already cleared by the time {@code
     * InstanceFlush} got around to calling it, so every texture lookup silently came back {@code
     * null} and crashed the very next {@code computeIfAbsent} with a null key.
     */
    public record Batch(ResourceLocation texture, InstanceData data) {
    }

    private static final Map<Primitive, InstanceData> SUBMISSIONS = new IdentityHashMap<>();
    private static final Map<Primitive, ResourceLocation> TEXTURES = new IdentityHashMap<>();

    private InstanceBatchCollector() {
    }

    public static void submit(Primitive primitive, ResourceLocation texture, Matrix4f modelView, Matrix3f normalMatrix, int overlay, int light) {
        long currentFrame = RenderFrameClock.currentFrame();
        SUBMISSIONS.computeIfAbsent(primitive, p -> new InstanceData()).add(currentFrame, modelView, normalMatrix, overlay, light);
        TEXTURES.putIfAbsent(primitive, texture);
    }

    /**
     * Everything submitted so far this frame. Reads each entry exactly as it currently stands — no
     * mutation happens here at all (see this class's own doc for the real bug an earlier "reset on
     * drain" version caused). A primitive is only included if its {@link InstanceData#frame()} matches
     * {@code currentFrame} <em>and</em> its {@link InstanceData#count()} is positive — the frame check
     * is load-bearing, not redundant: without it, a primitive whose last live entity just despawned (so
     * {@link InstanceData#add} simply never runs again) would keep reporting whatever {@code count} it
     * last held forever, since nothing else ever resets it — the exact bug that made every NPC removed
     * by {@code /sme npc clear} keep "drawing" at its last known transforms afterward.
     */
    public static Map<Primitive, Batch> drainAndClear() {
        if (SUBMISSIONS.isEmpty()) {
            return Map.of();
        }
        long currentFrame = RenderFrameClock.currentFrame();
        Map<Primitive, Batch> drained = new IdentityHashMap<>();
        for (Map.Entry<Primitive, InstanceData> entry : SUBMISSIONS.entrySet()) {
            InstanceData data = entry.getValue();
            if (data.frame() == currentFrame && data.count() > 0) {
                drained.put(entry.getKey(), new Batch(TEXTURES.get(entry.getKey()), data));
            }
        }
        return drained;
    }

    /**
     * Drops every accumulated {@link InstanceData}/texture entry — called on a model reload, since
     * {@link #SUBMISSIONS}/{@link #TEXTURES} persist across frames on their own (see this class's own
     * doc) rather than being wiped every frame. A reload parses brand-new {@link Primitive} objects for
     * every model, so without this, every old entry would sit forever as dead weight keyed by a {@link
     * Primitive} nothing else references any more — the same leak-on-reload class of bug {@code
     * InstanceFlush.clearCache()} already guards against for its own GL buffers.
     */
    public static void clearCache() {
        SUBMISSIONS.clear();
        TEXTURES.clear();
    }
}
