package com.dimalab.storymodengine.common.model.rig;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gives a flat, unrigged model a skeleton — by <b>rebuilding it as a real hierarchy</b> rather than
 * by carrying a separate animation-time indirection.
 *
 * <p>Why this exists: a Blockbench (or similar) export is typically a flat list of separate mesh
 * parts with no parent-child relationships and no skin. Each body part usually carries a sensible
 * pivot in its own node origin, but parts that must move <i>together</i> don't know about each other,
 * and accessory parts (armour, face details) often sit at the world origin with their geometry baked
 * in absolute coordinates. Rotating "the head" therefore leaves the helmet, brows and eyes behind.
 *
 * <p>Applying a rig inserts one group node per {@link RigBone} at that joint's pivot, and reparents
 * each member underneath with its translation made relative to that pivot. The result is an ordinary
 * {@link ModelDefinition} whose tree composes exactly like a natively-rigged model's would — so
 * {@code client.model.RuntimeNode}'s hierarchy pass, the renderer, and bone lookup for attachments
 * all work unchanged. Nothing downstream needs to know a rig was involved.
 *
 * <p>This is not skinning: parts stay rigid and move as wholes, which is precisely right for a
 * blocky, part-based model and costs none of the per-vertex work real skinning would.
 */
public final class ModelRig {

    private ModelRig() {
    }

    public static ModelDefinition apply(ModelDefinition source, List<RigBone> bones) {
        Map<String, List<ModelNode>> byName = new LinkedHashMap<>();
        for (ModelNode node : source.allNodes()) {
            byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
        }
        warnOnDuplicateNames(source.id(), byName);

        // Absolute pivot per bone, read from the anchor node's own origin.
        Map<String, Vector3f> pivots = new LinkedHashMap<>();
        for (RigBone bone : bones) {
            List<ModelNode> anchors = byName.get(bone.anchor());
            if (anchors == null || anchors.isEmpty()) {
                EngineLog.channel("Model").warn("{}: rig bone '{}' anchors on missing node '{}' — pivoting at the origin",
                        source.id(), bone.name(), bone.anchor());
                pivots.put(bone.name(), new Vector3f());
            } else {
                pivots.put(bone.name(), new Vector3f(anchors.get(0).bindTranslation()));
            }
        }

        Map<String, ModelNode> groups = new LinkedHashMap<>();
        List<ModelNode> roots = new ArrayList<>();
        int nextIndex = nextFreeIndex(source);

        for (RigBone bone : bones) {
            Vector3f pivot = pivots.get(bone.name());
            Vector3f parentPivot = bone.parent() == null ? new Vector3f() : pivots.getOrDefault(bone.parent(), new Vector3f());
            // A group's own translation is relative to its parent group, exactly as a glTF node's is.
            Vector3f local = pivot.sub(parentPivot, new Vector3f());

            ModelNode group = new ModelNode(nextIndex++, bone.name(), local, new Quaternionf(), new Vector3f(1f, 1f, 1f), null, null);
            groups.put(bone.name(), group);

            if (bone.parent() == null) {
                roots.add(group);
            } else {
                ModelNode parentGroup = groups.get(bone.parent());
                if (parentGroup == null) {
                    EngineLog.channel("Model").warn("{}: rig bone '{}' names parent '{}', which isn't declared before it — treating as a root",
                            source.id(), bone.name(), bone.parent());
                    roots.add(group);
                } else {
                    parentGroup.addChild(group);
                }
            }
        }

        Set<ModelNode> claimed = new HashSet<>();
        for (RigBone bone : bones) {
            ModelNode group = groups.get(bone.name());
            Vector3f pivot = pivots.get(bone.name());
            for (String memberName : bone.members()) {
                ModelNode member = claimNext(byName, memberName, claimed);
                if (member == null) {
                    EngineLog.channel("Model").warn("{}: rig bone '{}' lists member '{}', which has no unclaimed node",
                            source.id(), bone.name(), memberName);
                    continue;
                }
                group.addChild(reparent(member, pivot, nextIndex++));
            }
        }

        // Anything the rig didn't mention still has to render — hang it off the first root so it keeps
        // its place instead of silently disappearing.
        List<ModelNode> orphans = new ArrayList<>();
        for (ModelNode node : source.allNodes()) {
            if (!claimed.contains(node) && node.mesh() != null) {
                orphans.add(node);
            }
        }
        if (!orphans.isEmpty()) {
            ModelNode host = roots.isEmpty() ? null : roots.get(0);
            Vector3f hostPivot = host == null ? new Vector3f() : pivots.getOrDefault(host.name(), new Vector3f());
            EngineLog.channel("Model").debug("{}: {} node(s) not covered by the rig — attached to '{}'",
                    source.id(), orphans.size(), host == null ? "(none)" : host.name());
            for (ModelNode orphan : orphans) {
                ModelNode reparented = reparent(orphan, hostPivot, nextIndex++);
                if (host == null) {
                    roots.add(reparented);
                } else {
                    host.addChild(reparented);
                }
            }
        }

        return new ModelDefinition(source.id(), roots, source.animations(), source.metadata());
    }

    /** A copy of {@code node} whose translation is relative to {@code pivot}; its mesh is shared, not duplicated. */
    private static ModelNode reparent(ModelNode node, Vector3f pivot, int index) {
        Vector3f local = node.bindTranslation().sub(pivot, new Vector3f());
        return new ModelNode(index, node.name(), local, new Quaternionf(node.bindRotation()),
                new Vector3f(node.bindScale()), node.mesh(), node.skin());
    }

    private static ModelNode claimNext(Map<String, List<ModelNode>> byName, String name, Set<ModelNode> claimed) {
        List<ModelNode> candidates = byName.get(name);
        if (candidates == null) {
            return null;
        }
        for (ModelNode candidate : candidates) {
            if (claimed.add(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int nextFreeIndex(ModelDefinition source) {
        int max = 0;
        for (ModelNode node : source.allNodes()) {
            max = Math.max(max, node.index());
        }
        return max + 1;
    }

    private static void warnOnDuplicateNames(ResourceLocation id, Map<String, List<ModelNode>> byName) {
        for (Map.Entry<String, List<ModelNode>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                EngineLog.channel("Model").warn("{}: {} nodes share the name '{}' — rig members claim them in declaration order; rename them in the source model to remove the ambiguity",
                        id, entry.getValue().size(), entry.getKey());
            }
        }
    }
}
