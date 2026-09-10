package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.VertexData;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * One unskinned {@link Primitive}'s GPU-side state for the instanced draw path: the mesh's own
 * per-vertex data (Position/Color/UV0/Normal, plus {@code at_tangent} for packs that declare it),
 * uploaded once and shared by every entity that draws this primitive, plus
 * a per-instance attribute buffer rebuilt each frame from that frame's {@link
 * InstanceBatchCollector.InstanceData}.
 *
 * <p>Mirrors {@link GpuSkinBuffers}' construction style: attribute locations are queried from the
 * compiled shader by name rather than assumed, since a silent mismatch would misrender instead of
 * failing loudly. The per-vertex locations come from {@code DefaultVertexFormat.NEW_ENTITY}'s own
 * auto-bound range (0-6); the per-instance ones ({@code InstanceModelView}, {@code
 * InstanceNormalMatrix}, {@code InstanceOverlay}, {@code InstanceLight}) come from the explicit
 * {@code layout(location = ...)} block in {@code gltf_entity_instanced.vsh} — querying them by name
 * instead of hardcoding the numbers keeps this correct even if that block is ever renumbered without
 * this file being touched.
 *
 * <h2>Iris attribute renaming — audited, not reactive</h2>
 * {@link #resolveAttribute} exists because Iris renames some of a pack's own vertex inputs and injects a few
 * more that don't come from the pack at all — see that method and {@link #instanceOverlayLoc}/{@link
 * #instanceLightLoc}'s own docs for the specific renames this class binds around. That list was assembled once,
 * by disassembling {@code net.irisshaders.iris.pipeline.transform.transformer.VanillaTransformer} and {@code
 * EntityPatcher} in full and cross-checking every {@code in}/{@code uniform}/{@code attribute} declaration they
 * inject against what a plain vertex+fragment donor (no geometry or tessellation stage — {@code
 * ProgramSourceAccessor.sme$create} passes a donor's geometry/tessellation source through unmodified if either
 * exists, which this engine has never tested against and doesn't claim to support) can actually reach: {@code
 * iris_Position}/{@code iris_Normal}/{@code iris_Color}/{@code iris_UV0}/{@code iris_UV1}/{@code
 * iris_entityInfo} are all covered below. Left out on purpose, not by oversight: {@code iris_Entity}/{@code
 * iris_vertexColor} and their {@code TCS}/{@code TES}/{@code GS}-suffixed siblings only exist when a geometry or
 * tessellation stage is present in the chain — a real donor limitation, but a different, larger one than an
 * unbound attribute.
 *
 * <p>The per-instance buffer packs one {@code mat4} + one {@code mat3} + two {@code ivec2}s per
 * instance (116 bytes) — a {@code mat4}/{@code mat3} input attribute occupies one location per
 * column (confirmed: {@code glGetAttribLocation} on a multi-column attribute returns its first
 * column's location), so binding it is one {@code glVertexAttribPointer}/{@code
 * glVertexAttribDivisor} pair per column, not one for the whole matrix.
 */
public final class InstanceBatchBuffers {

    private static final int BYTES_PER_INSTANCE = 16 * 4 + 9 * 4 + 2 * 4 + 2 * 4; // mat4 + mat3 + ivec2 + ivec2

    private final int vao;
    private final int positionVbo;
    private final int colorVbo;
    private final int uv0Vbo;
    private final int normalVbo;
    private final int tangentVbo;
    private final int indexVbo;
    private final int indexCount;

    private final int instanceModelViewLoc;
    private final int instanceNormalMatrixLoc;

    /**
     * Binds to whichever of "InstanceOverlay" (this engine's own explicit-location declaration) or
     * {@code iris_UV1} (Iris's {@code EntityPatcher}, injected into <em>every</em> entity program it patches,
     * pack-independent) resolves — see {@link #resolveAttribute}'s own doc for why the second one is, in
     * practice, always the one that does: {@code InstanceOverlay} is declared but never read by anything this
     * engine's own merge writes, so a driver eliminates it from the linked program before this ever runs.
     */
    private final int instanceOverlayLoc;

    /**
     * Binds "InstanceLight" only, unlike {@link #instanceOverlayLoc} — this one has no Iris-name fallback, and
     * shouldn't be given one under {@code iris_UV2}. {@code iris_UV2} is Iris's rename of a donor's own literal
     * {@code gl_MultiTexCoord2}, an identifier this engine's merge never touches and packs practically never use
     * for entities; it has nothing to do with lightmap. {@code InstanceLight} instead comes from substituting
     * the donor's own {@code gl_MultiTexCoord1} — the identifier BSL and Complementary's real {@code
     * gbuffers_entities.vsh} both use for lightmap UV — directly in this engine's own merge, before Iris's own
     * patch pass ever runs on the result (see {@code InstancedVertexMerger}). Because that substitution is
     * unconditional and always references {@code InstanceLight}, the attribute is never left unused, so it is
     * never eliminated, so it always resolves under this engine's own name — falling through to {@code
     * iris_UV2} would only ever fire for a donor that skips {@code gl_MultiTexCoord1} entirely, and would then
     * bind lightmap data into an unrelated slot rather than degrading safely.
     */
    private final int instanceLightLoc;

    /**
     * {@code iris_entityInfo} — entity/block-entity/item ids Iris's own {@code EntityPatcher} injects into a
     * pack's entity program so it can special-case particular vanilla entities. Resolved by name, {@code -1}
     * when no pack is active. See {@link #resolveAttribute} for why an unfed attribute is a real bug rather
     * than a harmless zero, and {@link #draw} for what this one is set to.
     */
    private final int irisEntityInfoLoc;

    private int instanceVbo = -1;
    private int instanceCapacity = 0;

    // Reused across frames instead of ByteBuffer.allocateDirect-ing fresh every draw() call — the
    // same lag-causing anti-pattern found in GpuSkinBuffers, here scaling with instance count instead
    // of joint count. Grown (never shrunk) on demand rather than pre-sized, since instance count
    // varies frame to frame with how many entities share this primitive.
    private ByteBuffer instanceDataScratch;

    public InstanceBatchBuffers(Primitive primitive) {
        VertexData vertices = primitive.vertices();
        int vertexCount = vertices.vertexCount();
        indexCount = primitive.indices().length;

        ShaderInstance shader = InstancedShader.get();
        if (shader == null) {
            throw new IllegalStateException("instanced shader is not loaded yet");
        }
        int programId = shader.getId();
        int positionLoc = resolveAttribute(programId, "Position", "iris_Position");
        int colorLoc = resolveAttribute(programId, "Color", "iris_Color");
        int uv0Loc = resolveAttribute(programId, "UV0", "iris_UV0");
        int normalLoc = resolveAttribute(programId, "Normal", "iris_Normal");
        // No engine-name fallback here, unlike the four above: those exist as a *vanilla* convention
        // Iris renames, but tangent is purely additive - it only ever appears under Iris's own fixed
        // name (net.irisshaders.iris.gl.shader.ProgramCreator.create binds it to location 13 for
        // every program Iris links), and resolves to -1 (a clean no-op via uploadFloats' own
        // location<0 check) whenever no pack is active or the active one doesn't need it.
        int tangentLoc = GL20.glGetAttribLocation(programId, "at_tangent");
        // Deliberately NOT resolved/fed at all: "mc_midTexCoord" (BSL's own Parallax Occlusion Mapping
        // option reads it, via parallax.glsl) is a terrain-atlas concept — "the UV center of the
        // current tile in the shared atlas" — with no honest equivalent for a standalone, non-atlased
        // model texture. Three different fed values were tried (a recalculated per-triangle centroid,
        // the same data as UV0, a constant (0.5,0.5)) and none resolved the artifact; one of them (UV0)
        // turned out to guarantee a 0/0 NaN inside BSL's own GetParallaxShadow, which divides by a
        // quantity derived from this exact attribute. HollowEngine's own skinned/rigged characters
        // don't feed it either (confirmed by reading PipelineRenderer.kt — only their separate, static-
        // object-only "runtime instanced" binding does). Parallax needs real per-texel height data this
        // engine's LabPBR conversion never had a source for (an explicit non-goal from the original PBR
        // plan) — it is one shaderpack's own bonus effect on top of PBR support, not part of it, and is
        // explicitly out of scope here.
        instanceModelViewLoc = GL20.glGetAttribLocation(programId, "InstanceModelView");
        instanceNormalMatrixLoc = GL20.glGetAttribLocation(programId, "InstanceNormalMatrix");
        // "InstanceOverlay" is declared by this engine's own injected attribute block but never referenced by
        // anything this engine's merge writes (see this field's own doc) - a driver is free to (and observed to)
        // eliminate a declared-but-unused input from the linked program, so glGetAttribLocation("InstanceOverlay")
        // returning -1 here is the normal case, not a fallback path only exercised in some edge case. "iris_UV1"
        // is genuinely the only name that resolves in practice.
        instanceOverlayLoc = resolveAttribute(programId, "InstanceOverlay", "iris_UV1");
        instanceLightLoc = GL20.glGetAttribLocation(programId, "InstanceLight");
        irisEntityInfoLoc = GL20.glGetAttribLocation(programId, "iris_entityInfo");

        warnAboutUnresolvedAttributes(positionLoc, colorLoc, uv0Loc, normalLoc);

        vao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(vao);

        positionVbo = uploadFloats(vertices.positions(), 3, positionLoc);
        normalVbo = uploadFloats(vertices.normals(), 3, normalLoc);
        uv0Vbo = uploadFloats(vertices.hasUv() ? vertices.uv0() : new float[vertexCount * 2], 2, uv0Loc);
        colorVbo = uploadConstantColor(primitive.material().baseColorFactor(), vertexCount, colorLoc);
        tangentVbo = uploadFloats(vertices.hasTangents() ? vertices.tangents() : new float[vertexCount * 4], 4, tangentLoc);

        indexVbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
        IntBuffer indexData = ByteBuffer.allocateDirect(indexCount * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        indexData.put(primitive.indices()).flip();
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, indexData, GL33.GL_STATIC_DRAW);

        GL33.glBindVertexArray(0);
    }

    /**
     * Rebuilds the per-instance buffer from this frame's submissions and issues one {@code
     * glDrawElementsInstanced} call covering all of them. Caller has already called the instanced
     * {@code RenderType}'s {@code setupRenderState()} and applied the shader's uniforms.
     */
    public void draw(InstanceBatchCollector.InstanceData instanceData) {
        int count = instanceData.count();
        int neededBytes = count * BYTES_PER_INSTANCE;
        if (instanceDataScratch == null || instanceDataScratch.capacity() < neededBytes) {
            instanceDataScratch = ByteBuffer.allocateDirect(neededBytes).order(ByteOrder.nativeOrder());
        }
        ByteBuffer data = instanceDataScratch;
        data.clear();
        float[] matrixData = instanceData.matrixData();
        int[] overlayLight = instanceData.overlayLight();
        for (int i = 0; i < count; i++) {
            int offset = i * 25;
            for (int j = 0; j < 25; j++) {
                data.putFloat(matrixData[offset + j]);
            }
            int overlay = overlayLight[i * 2];
            int light = overlayLight[i * 2 + 1];
            data.putInt(overlay & 0xFFFF);
            data.putInt(overlay >> 16 & 0xFFFF);
            data.putInt(light & 0xFFFF);
            data.putInt(light >> 16 & 0xFFFF);
        }
        data.flip();

        GL33.glBindVertexArray(vao);
        boolean freshBuffer = instanceVbo < 0;
        if (freshBuffer) {
            instanceVbo = GL33.glGenBuffers();
        }
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, instanceVbo);
        if (freshBuffer || count > instanceCapacity) {
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, data, GL33.GL_STREAM_DRAW);
            instanceCapacity = count;
        } else {
            GL33.glBufferSubData(GL33.GL_ARRAY_BUFFER, 0L, data);
        }
        if (freshBuffer) {
            // Safe to bind once and never again: glVertexAttribPointer associates the attribute with
            // the currently-bound buffer OBJECT (its id), not a specific allocation — a later
            // glBufferData that resizes this same object leaves the VAO's binding valid.
            bindInstanceAttributes();
        }

        // The other injected attribute from irisOverlayLoc's doc — entity/block-entity/item ids a pack matches
        // against to special-case specific vanilla entities. This engine's geometry is none of them, so the
        // honest value is "no id at all"; left unfed it would read a stale current value that could collide
        // with a real id and trip a pack's special case at random. Set as a constant rather than a buffer
        // because it is genuinely the same for every instance, and unlike the attribute arrays above this is
        // context state, not VAO state, so it has to be re-set per draw rather than once at bind time.
        if (irisEntityInfoLoc >= 0) {
            GL30.glVertexAttribI4i(irisEntityInfoLoc, 0, 0, 0, 0);
        }

        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
        GL33.glDrawElementsInstanced(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L, count);
        GL33.glBindVertexArray(0);
        // BufferUploader caches which VertexBuffer's VAO is currently bound (lastImmediateBuffer) so
        // it can skip a redundant glBindVertexArray on repeated draws through the same one — but that
        // cache only tracks binds made through BufferUploader itself. Binding a DIFFERENT VAO here
        // behind its back leaves the cache pointing at a buffer that is no longer actually bound, so
        // the very next immediate draw (e.g. a held item drawn right after this) can skip its own
        // rebind and run against whatever VAO we left active — confirmed by "Array object is not
        // active" GL errors appearing exactly alongside a flickering held item. invalidate() clears
        // that cache so the next immediate draw always rebinds for real.
        BufferUploader.invalidate();
    }

    public void destroy() {
        GL33.glDeleteVertexArrays(vao);
        int[] buffers = instanceVbo >= 0
                ? new int[]{positionVbo, normalVbo, uv0Vbo, colorVbo, tangentVbo, indexVbo, instanceVbo}
                : new int[]{positionVbo, normalVbo, uv0Vbo, colorVbo, tangentVbo, indexVbo};
        GL33.glDeleteBuffers(buffers);
    }

    private void bindInstanceAttributes() {
        for (int col = 0; col < 4 && instanceModelViewLoc >= 0; col++) {
            int loc = instanceModelViewLoc + col;
            GL33.glVertexAttribPointer(loc, 4, GL11.GL_FLOAT, false, BYTES_PER_INSTANCE, col * 16L);
            GL33.glEnableVertexAttribArray(loc);
            GL33.glVertexAttribDivisor(loc, 1);
        }
        for (int col = 0; col < 3 && instanceNormalMatrixLoc >= 0; col++) {
            int loc = instanceNormalMatrixLoc + col;
            GL33.glVertexAttribPointer(loc, 3, GL11.GL_FLOAT, false, BYTES_PER_INSTANCE, 64L + col * 12L);
            GL33.glEnableVertexAttribArray(loc);
            GL33.glVertexAttribDivisor(loc, 1);
        }
        if (instanceOverlayLoc >= 0) {
            GL33.glVertexAttribIPointer(instanceOverlayLoc, 2, GL11.GL_INT, BYTES_PER_INSTANCE, 100L);
            GL33.glEnableVertexAttribArray(instanceOverlayLoc);
            GL33.glVertexAttribDivisor(instanceOverlayLoc, 1);
        }
        if (instanceLightLoc >= 0) {
            GL33.glVertexAttribIPointer(instanceLightLoc, 2, GL11.GL_INT, BYTES_PER_INSTANCE, 108L);
            GL33.glEnableVertexAttribArray(instanceLightLoc);
            GL33.glVertexAttribDivisor(instanceLightLoc, 1);
        }
    }

    /**
     * Finds one attribute under either the name this engine's own {@code gltf_entity_instanced.vsh} declares
     * for it, or the name Iris renames that same input to when a shader pack's program is the one bound —
     * exactly one of the two exists in any given compiled program, so whichever resolves is the right one.
     *
     * <p>This has to be by-name-with-a-fallback rather than just this engine's own name, because Iris does not
     * merely leave a pack's vertex inputs alone: its {@code VanillaTransformer} rewrites a donor's own literal
     * {@code gl_Color} to {@code (iris_Color * iris_ColorModulator)}, {@code gl_MultiTexCoord0} to {@code
     * iris_UV0}, and {@code gl_MultiTexCoord2} to {@code vec4(iris_UV2, 0.0, 1.0)} — declaring {@code
     * iris_Color}/{@code iris_UV0}/{@code iris_UV2} itself (confirmed by disassembling the real Oculus jar).
     * {@code gl_MultiTexCoord1} is <em>not</em> one of these: {@code InstancedVertexMerger} already substitutes
     * it directly, at the text level, before Iris's own patch pass ever sees the source — see {@link
     * #instanceLightLoc}'s own doc for why that makes an {@code iris_UV2} fallback wrong for lightmap
     * specifically, not merely redundant.
     *
     * <p>An attribute a program declares but nothing feeds does not read as zero — it reads GL's "current
     * value" for that attribute index, defaulting to {@code (0, 0, 0, 1)}. Missing {@code iris_Color} therefore
     * meant every instanced entity multiplied its texture by black under a pack that samples it ({@code albedo
     * = texture2D(texture, texCoord) * color} in BSL's {@code gbuffers_entities}), rendering the model solid
     * black — a bug that stayed hidden for a while behind a second one, a red {@code entityColor} tint (from
     * an equally unfed {@code iris_UV1}, which indexes row {@code v = 0} of vanilla's overlay texture: the
     * damage flash) mixing red into that black and reading as dark red rather than as broken.
     *
     * <p>The engine-side name is tried first so a build with no pack active — where this engine's own shader
     * is bound and no {@code iris_*} name exists at all — resolves without a second lookup.
     */
    private static int resolveAttribute(int programId, String engineName, String irisName) {
        int location = GL20.glGetAttribLocation(programId, engineName);
        return location >= 0 ? location : GL20.glGetAttribLocation(programId, irisName);
    }

    private static boolean warnedAboutUnresolvedAttributes;

    /**
     * A per-vertex input that resolves to {@code -1} under <em>every</em> name {@link #resolveAttribute} knows
     * is not a harmless no-op — the shader still reads that input, just as a stale constant, and the result is
     * a silently miscoloured or mis-textured model rather than an error (see {@link #resolveAttribute}'s own
     * doc for the two real bugs of exactly this shape). Nothing here can fix it automatically — a program using
     * some third naming convention needs a real code change — so this says so once, loudly enough to be found
     * from a screenshot of the wrong colour, instead of leaving it to be re-derived from scratch.
     */
    private static void warnAboutUnresolvedAttributes(int positionLoc, int colorLoc, int uv0Loc, int normalLoc) {
        if (warnedAboutUnresolvedAttributes || (positionLoc >= 0 && colorLoc >= 0 && uv0Loc >= 0 && normalLoc >= 0)) {
            return;
        }
        warnedAboutUnresolvedAttributes = true;
        EngineLog.channel("Model").warn("Instanced draw: some per-vertex attributes were not found in the bound program "
                        + "(Position={}, Color={}, UV0={}, Normal={}; -1 means not found under this engine's own name or Iris's). "
                        + "Those inputs will read a stale constant instead of real data, which shows up as wrong colour or texturing.",
                positionLoc, colorLoc, uv0Loc, normalLoc);
    }

    private static int uploadFloats(float[] data, int components, int location) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = ByteBuffer.allocateDirect(Math.max(data.length, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW);
        if (location >= 0) {
            GL33.glVertexAttribPointer(location, components, GL11.GL_FLOAT, false, 0, 0L);
            GL33.glEnableVertexAttribArray(location);
        }
        return vbo;
    }

    /** Every vertex gets the same material tint — cheaper than a real per-vertex color source, and correct since this engine's materials carry only a single constant factor. */
    private static int uploadConstantColor(float[] factor, int vertexCount, int location) {
        float r = factor.length > 0 ? factor[0] : 1f;
        float g = factor.length > 1 ? factor[1] : 1f;
        float b = factor.length > 2 ? factor[2] : 1f;
        float a = factor.length > 3 ? factor[3] : 1f;
        FloatBuffer buffer = ByteBuffer.allocateDirect(vertexCount * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < vertexCount; i++) {
            buffer.put(r).put(g).put(b).put(a);
        }
        buffer.flip();
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW);
        if (location >= 0) {
            GL33.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false, 0, 0L);
            GL33.glEnableVertexAttribArray(location);
        }
        return vbo;
    }
}
