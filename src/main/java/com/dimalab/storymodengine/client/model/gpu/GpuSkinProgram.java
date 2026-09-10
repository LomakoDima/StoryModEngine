package com.dimalab.storymodengine.client.model.gpu;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;

/**
 * The transform-feedback vertex program that skins (and, if a primitive has them, morphs) a mesh
 * on the GPU — see {@code gltf_skin_morph.vsh} and {@code GpuSkinBuffers}, which drives it.
 *
 * <p>Deliberately <b>not</b> a Minecraft {@code ShaderInstance}/Core Shader: this program is never
 * bound as "the current shader" for a normal draw — it only ever runs with rasterization disabled
 * to capture its outputs via transform feedback, so it needs no fragment shader and no registration
 * with Minecraft's own shader system. The actual textured draw afterwards reuses vanilla's real
 * compiled entity-cutout shader instead of a hand-authored one — see {@code GpuSkinBuffers} for why
 * that's possible.
 *
 * <p>One instance for the whole game, compiled lazily on first use, on the render thread — the same
 * lifecycle HollowEngine's own {@code createSkinningProgramGL33} uses.
 */
public final class GpuSkinProgram {

    private static final ResourceLocation SOURCE =
            new ResourceLocation(StoryModEngine.MODID, "shaders/core/gltf_skin_morph.vsh");

    private static GpuSkinProgram instance;

    private final int programId;
    private final int jointMatricesUniform;
    private final int morphDeltasPositionUniform;
    private final int morphDeltasNormalUniform;
    private final int morphWeightsUniform;
    private final int activeMorphCountUniform;
    private final int vertexCountUniform;
    private final int poseMatrixUniform;
    private final int poseNormalMatrixUniform;

    private GpuSkinProgram() {
        int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(shader, readSource());
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("gltf_skin_morph.vsh failed to compile:\n" + log);
        }

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, shader);
        // Must be declared before linking — this is what turns "just a vertex shader" into a
        // program transform feedback can capture from, per the GL spec.
        GL30.glTransformFeedbackVaryings(programId, new CharSequence[]{"outPosition", "outNormal", "outTangent"}, GL30.GL_SEPARATE_ATTRIBS);
        GL20.glLinkProgram(programId);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(programId);
            throw new IllegalStateException("gltf_skin_morph program failed to link:\n" + log);
        }

        jointMatricesUniform = GL20.glGetUniformLocation(programId, "jointMatrices");
        morphDeltasPositionUniform = GL20.glGetUniformLocation(programId, "morphDeltasPosition");
        morphDeltasNormalUniform = GL20.glGetUniformLocation(programId, "morphDeltasNormal");
        morphWeightsUniform = GL20.glGetUniformLocation(programId, "morphWeights");
        activeMorphCountUniform = GL20.glGetUniformLocation(programId, "activeMorphCount");
        vertexCountUniform = GL20.glGetUniformLocation(programId, "vertexCount");
        poseMatrixUniform = GL20.glGetUniformLocation(programId, "poseMatrix");
        poseNormalMatrixUniform = GL20.glGetUniformLocation(programId, "poseNormalMatrix");

        EngineLog.channel("Model").debug("GPU skinning program compiled and linked (id {})", programId);
    }

    private static String readSource() {
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(SOURCE)) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SOURCE, e);
        }
    }

    /** Compiled and linked on first call; every call after that returns the same instance. Must run on the render thread. */
    public static GpuSkinProgram get() {
        if (instance == null) {
            instance = new GpuSkinProgram();
        }
        return instance;
    }

    public int programId() {
        return programId;
    }

    public int jointMatricesUniform() {
        return jointMatricesUniform;
    }

    public int morphDeltasPositionUniform() {
        return morphDeltasPositionUniform;
    }

    public int morphDeltasNormalUniform() {
        return morphDeltasNormalUniform;
    }

    public int morphWeightsUniform() {
        return morphWeightsUniform;
    }

    public int activeMorphCountUniform() {
        return activeMorphCountUniform;
    }

    public int vertexCountUniform() {
        return vertexCountUniform;
    }

    public int poseMatrixUniform() {
        return poseMatrixUniform;
    }

    public int poseNormalMatrixUniform() {
        return poseNormalMatrixUniform;
    }
}
