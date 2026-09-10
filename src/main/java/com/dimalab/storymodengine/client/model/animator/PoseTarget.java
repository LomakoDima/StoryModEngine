package com.dimalab.storymodengine.client.model.animator;

import com.dimalab.storymodengine.client.model.RuntimeNode;
import com.dimalab.storymodengine.common.model.AnimationClip;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one {@code ModelInstance}'s layers pose against: its node tree (by index and by name/path) and
 * its named clips. Built once per {@code ModelInstance} and reused every frame — safe, and an
 * efficiency win over HollowEngine (which rebuilds the equivalent object per {@code applyTo} call),
 * since neither the node tree nor the clip set changes over an instance's lifetime.
 */
public final class PoseTarget {

    private final Map<Integer, RuntimeNode> nodesByIndex;
    private final Map<String, AnimationClip> animations;
    private final Map<String, RuntimeNode> nodesByName;
    private final Map<BoneMask, Set<Integer>> maskCache = new HashMap<>();

    public PoseTarget(Map<Integer, RuntimeNode> nodesByIndex, Map<String, AnimationClip> animations) {
        this.nodesByIndex = nodesByIndex;
        this.animations = animations;
        Map<String, RuntimeNode> byName = new LinkedHashMap<>();
        for (RuntimeNode node : nodesByIndex.values()) {
            byName.putIfAbsent(node.name(), node);
            byName.putIfAbsent(node.path(), node);
        }
        this.nodesByName = byName;
    }

    public Map<Integer, RuntimeNode> nodesByIndex() {
        return nodesByIndex;
    }

    /** Resolves a bone by its own name or by its full dotted path — null if nothing matches. */
    public RuntimeNode node(String name) {
        return nodesByName.get(name);
    }

    /** Null if no clip of that name exists on this model. */
    public AnimationClip animation(String name) {
        return animations.get(name);
    }

    /** Resolves (and caches) {@code mask} to the set of node indices it matches on this model's tree. */
    public Set<Integer> mask(BoneMask mask) {
        return maskCache.computeIfAbsent(mask, this::resolveMask);
    }

    private Set<Integer> resolveMask(BoneMask mask) {
        Set<Integer> result = new LinkedHashSet<>();
        for (RuntimeNode node : nodesByIndex.values()) {
            String name = node.name();
            String path = node.path();
            boolean included = mask.includes().isEmpty()
                    || mask.includes().stream().anyMatch(i -> i.equals(name) || path.endsWith(i));
            boolean excluded = mask.excludes().stream().anyMatch(x -> x.equals(name) || path.endsWith(x));
            if (included && !excluded) {
                result.add(node.definition().index());
            }
        }
        return result;
    }
}
