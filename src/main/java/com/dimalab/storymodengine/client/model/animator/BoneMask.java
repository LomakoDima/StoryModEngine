package com.dimalab.storymodengine.client.model.animator;

import java.util.Set;

/**
 * Which bones a layer touches, by name or dotted path suffix — a pure value; resolving it against a
 * particular model's node tree (and caching that resolution) is {@link PoseTarget}'s job, since the
 * same mask value gets reused across different model instances.
 *
 * <p>A node matches if {@code includes} is empty, or any include equals its name or its path ends
 * with it; it's then excluded if any exclude matches the same way — an exclude always wins over an
 * include. This is HollowEngine's own {@code BoneMask} matching rule, ported exactly.
 */
public record BoneMask(Set<String> includes, Set<String> excludes) {

    public static final BoneMask FULL = new BoneMask(Set.of(), Set.of());

    public BoneMask {
        includes = Set.copyOf(includes);
        excludes = Set.copyOf(excludes);
    }

    /** {@code "foo", "!bar"} — a leading {@code !} excludes, everything else includes. */
    public static BoneMask of(String... bones) {
        java.util.LinkedHashSet<String> includeSet = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> excludeSet = new java.util.LinkedHashSet<>();
        for (String bone : bones) {
            if (bone.startsWith("!")) {
                excludeSet.add(bone.substring(1));
            } else {
                includeSet.add(bone);
            }
        }
        return new BoneMask(includeSet, excludeSet);
    }
}
