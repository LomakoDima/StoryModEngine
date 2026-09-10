package com.dimalab.storymodengine.common.model.gltf;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a model (or a file it references) from a real folder on disk, next to the game's own run
 * directory — {@code <rundir>/storymodengine/assets/<namespace>/<path>}, mirroring the resource
 * pack's own {@code assets/<namespace>/<path>} layout so the same relative paths work in either
 * place. Checked <b>before</b> the resource pack / classpath (see {@link GltfBufferResolver}), so a
 * modpack author or artist can override or add a model without repackaging the mod — and checked
 * on both sides: the server reads through here for the same reason it already falls back to the
 * classpath in {@code ModelPhysics} — it has no {@code ResourceManager} of its own.
 *
 * <p>Ported from HollowEngine's own {@code ModelResourceIO.kt}, which uses the identical
 * resolve-then-normalize-then-{@code startsWith} guard: build the candidate path under the trusted
 * root, normalize it (collapsing {@code .}/{@code ..} segments), then reject anything that no
 * longer starts with that root once normalized. A relative path like
 * {@code ../../../../etc/passwd} normalizes outside the root and is rejected the same way a
 * legitimate sibling reference (`../shared/skin.png`) staying inside it is accepted. Like HE's own
 * guard, this does not defend against a symlink planted inside the root that points back outside
 * it — noted rather than silently assumed away, not solved here.
 */
public final class ExternalModelIO {

    private static final Path ROOT = FMLPaths.GAMEDIR.get().resolve("storymodengine").resolve("assets").normalize();

    private ExternalModelIO() {
    }

    /** Bytes for {@code location} under the external folder, or {@code null} if it isn't there (including a rejected traversal attempt) — never throws for "not found", only for a real I/O failure once the file is known to exist. */
    public static byte[] read(ResourceLocation location) {
        Path candidate = resolve(location);
        if (candidate == null || !Files.isRegularFile(candidate)) {
            return null;
        }
        try {
            return Files.readAllBytes(candidate);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("could not read external model file " + candidate, e);
        }
    }

    public static boolean exists(ResourceLocation location) {
        Path candidate = resolve(location);
        return candidate != null && Files.isRegularFile(candidate);
    }

    /** The path {@code location} would live at under the external root, or {@code null} if it normalizes outside that root. */
    private static Path resolve(ResourceLocation location) {
        Path candidate = ROOT.resolve(location.getNamespace()).resolve(location.getPath()).normalize();
        return candidate.startsWith(ROOT) ? candidate : null;
    }
}
