package com.dimalab.storymodengine.common.model.rig;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.ModelNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renames existing nodes to the engine's bone-name vocabulary, for a model that already has a real
 * hierarchy — unlike the flat exports {@link ModelRig} rebuilds from scratch. Nothing about the tree
 * changes: children, transforms, meshes all stay exactly where they were; only the handful of pivot
 * nodes {@code HumanoidPoser} looks up by name get relabelled.
 *
 * <p>Exists because a rigger names groups however reads naturally in their own tool ({@code
 * "LeftArm"}, {@code "Body"}), while {@code HumanoidBones} is the fixed vocabulary the engine
 * animates by. Running {@link ModelRig} on a model like this instead would discard its already-
 * correct hierarchy to rebuild an equivalent one from a member list — strictly more work for a worse
 * result.
 */
public final class ModelAliases {

    private ModelAliases() {
    }

    /** @param aliases engine bone name (e.g. {@code "leftArm"}) to this model's actual node name (e.g. {@code "LeftArm"}) */
    public static ModelDefinition apply(ModelDefinition source, Map<String, String> aliases) {
        if (aliases.isEmpty()) {
            return source;
        }
        List<ModelNode> roots = new ArrayList<>(source.roots());
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String engineName = entry.getKey();
            String actualName = entry.getValue();
            ModelNode target = source.nodeByName(actualName);
            if (target == null) {
                EngineLog.channel("Model").warn("{}: alias '{}' names missing node '{}'",
                        source.id(), engineName, actualName);
                continue;
            }

            ModelNode renamed = new ModelNode(target.index(), engineName, target.bindTranslation(),
                    target.bindRotation(), target.bindScale(), target.mesh(), target.skin());
            for (ModelNode child : target.children()) {
                renamed.addChild(child);
            }

            ModelNode parent = target.parent();
            if (parent == null) {
                int index = roots.indexOf(target);
                if (index >= 0) {
                    roots.set(index, renamed);
                }
            } else {
                parent.replaceChild(target, renamed);
            }
        }
        return new ModelDefinition(source.id(), roots, source.animations(), source.metadata());
    }
}
