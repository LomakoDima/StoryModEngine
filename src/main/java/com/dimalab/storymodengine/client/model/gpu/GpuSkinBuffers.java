package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.common.model.Primitive;
import com.dimalab.storymodengine.common.model.Skin;
import com.dimalab.storymodengine.common.model.VertexData;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * One skinned {@link Primitive}'s GPU-side state, for one {@code ModelInstance} — skin matrices and
 * morph weights differ per entity, so this cannot be shared across instances of the same model the
 * way the {@link Primitive}'s own vertex data is.
 *
 * <p>Two halves, matching the two things that happen every frame:
 * <ul>
 *   <li>{@link #updateAndSkin} runs the transform-feedback pass ({@link GpuSkinProgram}) that turns
 *       this frame's joint matrices (+ morph weights, if any) into skinned position/normal, written
 *       into {@link #outPositionVbo}/{@link #outNormalVbo}.</li>
 *   <li>{@link #draw} issues the actual textured draw — <b>through vanilla's own, real, compiled
 *       entity-cutout shader</b> ({@code GameRenderer.getRendertypeEntityCutoutNoCullShader()}),
 *       not a hand-authored one. Attribute locations are queried from that compiled program by
 *       name at construction time rather than assumed, since a wrong guess there would silently
 *       misrender rather than fail loudly.</li>
 * </ul>
 *
 * <p>Uses vanilla's exact {@code RenderType.setupRenderState()}/{@code clearRenderState()} —
 * verified against {@code LevelRenderer.renderChunkLayer}, which draws a persistent chunk {@code
 * VertexBuffer} through a {@code RenderType} the identical way. Drawing a persistent VBO through an
 * existing {@code RenderType} instead of a fresh {@code BufferBuilder} is exactly what that vanilla
 * code already does for terrain; this is the same technique applied to one skinned mesh.
 */
public final class GpuSkinBuffers {

    private static final int POSITION_LOC = 2;
    private static final int NORMAL_LOC = 3;
    private static final int JOINT_LOC = 0;
    private static final int WEIGHT_LOC = 1;
    private static final int TANGENT_LOC = 4;

    private final int vertexCount;
    private final int indexCount;
    private final int morphCount;

    private final int skinVao;
    private final int jointsVbo;
    private final int weightsVbo;
    private final int srcPositionVbo;
    private final int srcNormalVbo;
    private final int srcTangentVbo;

    private final int morphPosBuffer;
    private final int morphPosTexture;
    private final int morphNorBuffer;
    private final int morphNorTexture;

    private final int jointMatrixBuffer;
    private final int jointMatrixTexture;

    private final int outPositionVbo;
    private final int outNormalVbo;
    private final int outTangentVbo;

    private final int drawVao;
    private final int uv0Vbo;
    private final int colorVbo;
    private final int overlayVbo;
    private final int lightVbo;
    private final int indexVbo;

    private final int drawPositionLoc;
    private final int drawNormalLoc;
    private final int drawColorLoc;
    private final int drawUv0Loc;
    private final int drawUv1Loc;
    private final int drawUv2Loc;
    private final int drawTangentLoc;

    /** @see #builtForProgramId() */
    private final int builtForProgramId;

    private int lastOverlay = Integer.MIN_VALUE;
    private int lastLight = Integer.MIN_VALUE;

    // Reused every frame instead of ByteBuffer.allocateDirect-ing fresh ones — a direct allocation
    // per call was cheap to miss with a small rig, but a converted UE/Fortnite skin can carry 400+
    // joints (one real case: 448), and this runs once per skinned primitive every single frame. That
    // turned into measured, reported lag ("сложная модель начинает лагать") once a model like that
    // showed up, not just theoretical churn.
    private final FloatBuffer poseScratch = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer poseNormalScratch = ByteBuffer.allocateDirect(36).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer jointMatrixScratch;
    private final float[] jointMatrixColumn = new float[16];
    private final java.nio.ShortBuffer overlayLightScratch;
    private final float[] morphWeightsScratch = new float[64];

    public GpuSkinBuffers(Primitive primitive, Skin skin) {
        VertexData vertices = primitive.vertices();
        vertexCount = vertices.vertexCount();
        indexCount = primitive.indices().length;
        morphCount = primitive.morphTargets().size();
        jointMatrixScratch = ByteBuffer.allocateDirect(skin.jointCount() * 64).order(ByteOrder.nativeOrder()).asFloatBuffer();
        overlayLightScratch = ByteBuffer.allocateDirect(vertexCount * 2 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();

        // ---- the skin/morph transform-feedback source VAO ----
        skinVao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(skinVao);

        srcPositionVbo = uploadFloats(vertices.positions(), 3, POSITION_LOC);
        srcNormalVbo = uploadFloats(vertices.normals(), 3, NORMAL_LOC);
        jointsVbo = uploadJoints(vertices.joints());
        weightsVbo = uploadFloats(vertices.weights(), 4, WEIGHT_LOC);
        srcTangentVbo = uploadFloats(vertices.hasTangents() ? vertices.tangents() : new float[vertexCount * 4], 4, TANGENT_LOC);

        if (morphCount > 0) {
            int[] textures = createMorphTextures(primitive.morphTargets(), vertexCount);
            morphPosBuffer = textures[0];
            morphPosTexture = textures[1];
            morphNorBuffer = textures[2];
            morphNorTexture = textures[3];
        } else {
            morphPosBuffer = -1;
            morphPosTexture = -1;
            morphNorBuffer = -1;
            morphNorTexture = -1;
        }

        jointMatrixBuffer = GL33.glGenBuffers();
        GL33.glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointMatrixBuffer);
        GL33.glBufferData(GL31.GL_TEXTURE_BUFFER, (long) skin.jointCount() * 64L, GL33.GL_DYNAMIC_DRAW);
        jointMatrixTexture = createTextureBuffer(jointMatrixBuffer);

        GL33.glBindVertexArray(0);

        // ---- transform-feedback outputs ----
        outPositionVbo = allocateOutput(vertexCount * 3L * 4L);
        outNormalVbo = allocateOutput(vertexCount * 3L * 4L);
        outTangentVbo = allocateOutput(vertexCount * 4L * 4L);

        // ---- the real draw, through vanilla's own compiled shader ----
        ShaderInstance entityShader = GameRenderer.getRendertypeEntityCutoutNoCullShader();
        if (entityShader == null) {
            throw new IllegalStateException("vanilla's entity-cutout shader is not loaded yet");
        }
        int shaderProgram = entityShader.getId();
        builtForProgramId = shaderProgram;
        drawPositionLoc = GL20.glGetAttribLocation(shaderProgram, "Position");
        drawNormalLoc = GL20.glGetAttribLocation(shaderProgram, "Normal");
        drawColorLoc = GL20.glGetAttribLocation(shaderProgram, "Color");
        drawUv0Loc = GL20.glGetAttribLocation(shaderProgram, "UV0");
        drawUv1Loc = GL20.glGetAttribLocation(shaderProgram, "UV1");
        drawUv2Loc = GL20.glGetAttribLocation(shaderProgram, "UV2");
        // Purely additive - unlike the names above (a vanilla convention Iris renames), "at_tangent"
        // only ever exists under Iris's own fixed name (net.irisshaders.iris.gl.shader.ProgramCreator
        // .create binds it to location 13 for every program Iris links) and resolves to -1, a clean
        // no-op via bindFloatAttribute's own location<0 check, whenever no pack needs it.
        drawTangentLoc = GL20.glGetAttribLocation(shaderProgram, "at_tangent");
        // Deliberately NOT fed anywhere, on any path: "mc_midTexCoord" (BSL's own Parallax Occlusion
        // Mapping option specifically reads it, via parallax.glsl) is a terrain-atlas concept — "the
        // UV center of the current tile in the shared atlas" — with no honest equivalent for a
        // standalone, non-atlased model texture. Three different fed values were tried (a recalculated
        // per-triangle centroid, the same data as UV0, a constant (0.5,0.5)) and none resolved the
        // artifact; HollowEngine's own skinned/rigged characters (confirmed by reading PipelineRenderer
        // .kt: supportsInstancing = !isDynamic routes any skinned primitive through the plain, non-
        // pack-aware renderVAO()/initDynamicBuffers() path, which never binds mc_midTexCoord at all —
        // only their separate, static-object-only "runtime instanced" binding does) don't feed it
        // either. Parallax needs real per-texel height data this engine's LabPBR conversion never had a
        // source for and was never going to synthesize (an explicit non-goal from the original PBR
        // plan) — it is not part of this engine's PBR material support, just one shaderpack's own
        // bonus effect on top of it, and is explicitly out of scope here.
        drawVao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(drawVao);

        bindFloatAttribute(outPositionVbo, drawPositionLoc, 3, 0L);
        bindFloatAttribute(outNormalVbo, drawNormalLoc, 3, 0L);
        bindFloatAttribute(outTangentVbo, drawTangentLoc, 4, 0L);
        uv0Vbo = createStaticFloatBuffer(vertices.hasUv() ? vertices.uv0() : new float[vertexCount * 2]);
        bindFloatAttribute(uv0Vbo, drawUv0Loc, 2, 0L);
        colorVbo = createConstantColorBuffer(primitive.material().baseColorFactor(), vertexCount);
        bindFloatAttribute(colorVbo, drawColorLoc, 4, 0L);
        overlayVbo = createShortPairBuffer(vertexCount);
        bindShortAttribute(overlayVbo, drawUv1Loc);
        lightVbo = createShortPairBuffer(vertexCount);
        bindShortAttribute(lightVbo, drawUv2Loc);

        indexVbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
        java.nio.IntBuffer indexData = java.nio.ByteBuffer.allocateDirect(indexCount * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        indexData.put(primitive.indices()).flip();
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, indexData, GL33.GL_STATIC_DRAW);

        GL33.glBindVertexArray(0);
    }

    /**
     * Runs the skin(+morph) transform-feedback pass, writing this frame's pose into the output VBOs
     * {@link #draw} reads from. {@code pose}/{@code poseNormal} are the entity's own placement this
     * frame ({@code PoseStack.Pose}) — applied inside the shader after skinning, the GPU-side
     * equivalent of the CPU path's {@code poseMatrix.transformPosition(...)} on the final vertex.
     */
    public void updateAndSkin(Matrix4f[] skinMatrices, float[] morphWeights, Matrix4f pose, Matrix3f poseNormal) {
        updateJointMatrices(skinMatrices);

        GpuSkinProgram program = GpuSkinProgram.get();
        GL20.glUseProgram(program.programId());

        poseScratch.clear();
        pose.get(poseScratch);
        GL20.glUniformMatrix4fv(program.poseMatrixUniform(), false, poseScratch);

        poseNormalScratch.clear();
        poseNormal.get(poseNormalScratch);
        GL20.glUniformMatrix3fv(program.poseNormalMatrixUniform(), false, poseNormalScratch);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, jointMatrixTexture);
        GL20.glUniform1i(program.jointMatricesUniform(), 0);

        int activeMorphCount = 0;
        if (morphCount > 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphPosTexture);
            GL20.glUniform1i(program.morphDeltasPositionUniform(), 1);

            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphNorTexture);
            GL20.glUniform1i(program.morphDeltasNormalUniform(), 2);

            activeMorphCount = morphCount;
            if (morphWeights != null && morphWeights.length > 0) {
                GL20.glUniform1fv(program.morphWeightsUniform(), padWeights(morphWeights));
            }
        }
        GL20.glUniform1i(program.activeMorphCountUniform(), activeMorphCount);
        GL20.glUniform1i(program.vertexCountUniform(), vertexCount);

        GL33.glBindVertexArray(skinVao);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, outPositionVbo);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, outNormalVbo);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, outTangentVbo);

        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL11.glDrawArrays(GL11.GL_POINTS, 0, vertexCount);
        GL30.glEndTransformFeedback();
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);

        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, 0);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, 0);
        GL33.glBindVertexArray(0);
        GL20.glUseProgram(0);
        // Texture unit 0 is the ambient assumption the rest of Minecraft's rendering makes between
        // draws — RenderType.setupRenderState() (called right after this, for the actual textured
        // draw) binds the material texture without necessarily re-selecting a unit first. Leaving
        // GL_TEXTURE2 (or 1) active here, as the morph branch above does, would make that bind land
        // on the wrong unit and leave Sampler0 pointing at our joint-matrix buffer instead.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** The actual textured draw, through {@code renderType}'s own (vanilla) shader/texture/blend state. Caller has already called {@code renderType.setupRenderState()}. */
    public void draw(RenderType renderType, int packedOverlay, int packedLight) {
        if (packedOverlay != lastOverlay) {
            writeShortPair(overlayVbo, packedOverlay);
            lastOverlay = packedOverlay;
        }
        if (packedLight != lastLight) {
            writeShortPair(lightVbo, packedLight);
            lastLight = packedLight;
        }

        GL33.glBindVertexArray(drawVao);
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L);
        GL33.glBindVertexArray(0);
        // See InstanceBatchBuffers.draw() for why: BufferUploader caches which VertexBuffer's VAO is
        // bound to skip redundant rebinds, and that cache doesn't know about binds made outside it —
        // leaving it stale here would make the next immediate draw (e.g. a held item) skip its own
        // rebind and run against whatever VAO this call leaves active.
        BufferUploader.invalidate();
    }

    /** Test-only: lets {@code debug.ModelSelfTest} read back {@link #updateAndSkin}'s output and check it against {@code CpuSkinner}'s own formula — see that class's doc for why. */
    public int outPositionBufferId() {
        return outPositionVbo;
    }

    /** @see #outPositionBufferId() */
    public int outNormalBufferId() {
        return outNormalVbo;
    }

    public int vertexCount() {
        return vertexCount;
    }

    /**
     * The GL program id {@link #drawPositionLoc}/etc. were resolved against at construction time —
     * see {@code ModelInstance#gpuSkinBuffersFor} for why callers compare this against the entity
     * shader's <em>current</em> id before reusing a cached instance. A shaderpack option toggle (or a
     * plain vanilla resource/shader reload) can relink {@code GameRenderer
     * .getRendertypeEntityCutoutNoCullShader()} to a new compiled program — new id, and/or a
     * different implicit attribute layout even at the same id — and this class has no way to notice
     * that on its own, since {@link #draw} never re-resolves anything.
     */
    public int builtForProgramId() {
        return builtForProgramId;
    }

    public void destroy() {
        GL33.glDeleteVertexArrays(skinVao);
        GL33.glDeleteVertexArrays(drawVao);
        GL33.glDeleteBuffers(new int[]{jointsVbo, weightsVbo, srcPositionVbo, srcNormalVbo, srcTangentVbo,
                jointMatrixBuffer, outPositionVbo, outNormalVbo, outTangentVbo, uv0Vbo, colorVbo, overlayVbo, lightVbo, indexVbo});
        GL11.glDeleteTextures(jointMatrixTexture);
        if (morphCount > 0) {
            GL33.glDeleteBuffers(new int[]{morphPosBuffer, morphNorBuffer});
            GL11.glDeleteTextures(new int[]{morphPosTexture, morphNorTexture});
        }
    }

    // ---- construction helpers ----

    private static int uploadFloats(float[] data, int components, int location) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW);
        GL33.glVertexAttribPointer(location, components, GL11.GL_FLOAT, false, 0, 0L);
        GL33.glEnableVertexAttribArray(location);
        return vbo;
    }

    private static int uploadJoints(int[] joints) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        java.nio.IntBuffer buffer = ByteBuffer.allocateDirect(joints.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        buffer.put(joints).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW);
        GL30.glVertexAttribIPointer(JOINT_LOC, 4, GL11.GL_INT, 0, 0L);
        GL33.glEnableVertexAttribArray(JOINT_LOC);
        return vbo;
    }

    private static int[] createMorphTextures(java.util.List<Primitive.MorphTarget> targets, int vertexCount) {
        int count = targets.size();
        FloatBuffer posBuffer = ByteBuffer.allocateDirect(vertexCount * count * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer norBuffer = ByteBuffer.allocateDirect(vertexCount * count * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (Primitive.MorphTarget target : targets) {
            float[] pos = target.positionDeltas();
            float[] nor = target.normalDeltas();
            for (int i = 0; i < vertexCount; i++) {
                posBuffer.put(pos.length > 0 ? pos[i * 3] : 0f).put(pos.length > 0 ? pos[i * 3 + 1] : 0f).put(pos.length > 0 ? pos[i * 3 + 2] : 0f).put(0f);
                norBuffer.put(nor.length > 0 ? nor[i * 3] : 0f).put(nor.length > 0 ? nor[i * 3 + 1] : 0f).put(nor.length > 0 ? nor[i * 3 + 2] : 0f).put(0f);
            }
        }
        posBuffer.flip();
        norBuffer.flip();

        int posVbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL31.GL_TEXTURE_BUFFER, posVbo);
        GL33.glBufferData(GL31.GL_TEXTURE_BUFFER, posBuffer, GL33.GL_STATIC_DRAW);
        int posTex = createTextureBuffer(posVbo);

        int norVbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL31.GL_TEXTURE_BUFFER, norVbo);
        GL33.glBufferData(GL31.GL_TEXTURE_BUFFER, norBuffer, GL33.GL_STATIC_DRAW);
        int norTex = createTextureBuffer(norVbo);

        return new int[]{posVbo, posTex, norVbo, norTex};
    }

    private static int createTextureBuffer(int bufferId) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, bufferId);
        return textureId;
    }

    private static int allocateOutput(long bytes) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, bytes, GL33.GL_DYNAMIC_COPY);
        return vbo;
    }

    private static void bindFloatAttribute(int vbo, int location, int components, long offset) {
        if (location < 0) {
            return;
        }
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL33.glVertexAttribPointer(location, components, GL11.GL_FLOAT, false, 0, offset);
        GL33.glEnableVertexAttribArray(location);
    }

    private static void bindShortAttribute(int vbo, int location) {
        if (location < 0) {
            return;
        }
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL30.glVertexAttribIPointer(location, 2, GL11.GL_SHORT, 0, 0L);
        GL33.glEnableVertexAttribArray(location);
    }

    private static int createStaticFloatBuffer(float[] data) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = ByteBuffer.allocateDirect(Math.max(data.length, 1) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(data).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW);
        return vbo;
    }

    /** Every vertex gets the same material tint — cheaper than a real per-vertex color source, and correct since this engine's materials carry only a single constant factor. */
    private static int createConstantColorBuffer(float[] factor, int vertexCount) {
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
        return vbo;
    }

    private static int createShortPairBuffer(int vertexCount) {
        int vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, (long) vertexCount * 4L, GL33.GL_DYNAMIC_DRAW);
        return vbo;
    }

    private void writeShortPair(int vbo, int packed) {
        short u = (short) (packed & 0xFFFF);
        short v = (short) (packed >> 16 & 0xFFFF);
        overlayLightScratch.clear();
        for (int i = 0; i < vertexCount; i++) {
            overlayLightScratch.put(u).put(v);
        }
        overlayLightScratch.flip();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        GL33.glBufferSubData(GL33.GL_ARRAY_BUFFER, 0L, overlayLightScratch);
    }

    private void updateJointMatrices(Matrix4f[] matrices) {
        jointMatrixScratch.clear();
        for (Matrix4f matrix : matrices) {
            matrix.get(jointMatrixColumn);
            jointMatrixScratch.put(jointMatrixColumn);
        }
        jointMatrixScratch.flip();
        GL33.glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointMatrixBuffer);
        GL33.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, jointMatrixScratch);
    }

    private float[] padWeights(float[] weights) {
        int count = Math.min(weights.length, 64);
        System.arraycopy(weights, 0, morphWeightsScratch, 0, count);
        // Zero the tail left over from a previous, longer call — this scratch array is reused across
        // frames, unlike the fresh-every-call array it replaced.
        java.util.Arrays.fill(morphWeightsScratch, count, 64, 0f);
        return morphWeightsScratch;
    }
}
