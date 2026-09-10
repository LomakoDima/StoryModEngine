package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.common.entity.npc.NpcDefinition;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.registry.FlowRegistry;
import com.dimalab.storymodengine.common.scripting.ast.NpcDeclNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * {@code npc "<name>" { ... }} → {@link NpcDefinition} — named {@code SmeNpcCompiler} for the same
 * reason {@code SmeQuestCompiler} is (builds the definition from AST; nothing else in this codebase
 * shares the simple name {@code NpcCompiler}).
 *
 * <p>{@code onInteract}'s body compiles to a {@link Flow} exactly like a {@code trigger { run { } }}
 * body does (see {@link SequenceCompiler}), then registers it into {@link FlowRegistry} under a
 * synthetic id derived from the npc's own name — {@link NpcDefinition} keeps only that id, not the
 * raw {@code Flow}, so {@code NpcEntity.mobInteract} starts it the same way any other named flow
 * starts, through {@code FlowManager.start(id, player)}.
 */
public final class SmeNpcCompiler {

    private SmeNpcCompiler() {
    }

    public static NpcDefinition compile(NpcDeclNode node, CompileContext ctx) {
        ResourceLocation onInteractFlowId = null;
        if (!node.onInteract().isEmpty()) {
            Flow flow = new SequenceCompiler(ctx).compile(node.onInteract());
            onInteractFlowId = new ResourceLocation("storymodengine", "npc_interact/" + sanitize(node.name()));
            FlowRegistry.register(onInteractFlowId, flow);
        }
        ResourceLocation onShiftInteractFlowId = null;
        if (!node.onShiftInteract().isEmpty()) {
            Flow flow = new SequenceCompiler(ctx).compile(node.onShiftInteract());
            onShiftInteractFlowId = new ResourceLocation("storymodengine", "npc_interact/" + sanitize(node.name()) + "_shift");
            FlowRegistry.register(onShiftInteractFlowId, flow);
        }
        return new NpcDefinition(
                node.name(),
                node.model(),
                node.skin(),
                parseBehavior(node.behavior()),
                node.displayName() != null ? node.displayName() : node.name(),
                node.attributes(),
                new Vec3(node.x(), node.y(), node.z()),
                onInteractFlowId,
                onShiftInteractFlowId);
    }

    private static NpcBehavior parseBehavior(String text) {
        if (text == null) {
            return NpcBehavior.PASSIVE;
        }
        try {
            return NpcBehavior.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NpcBehavior.PASSIVE;
        }
    }

    /** Folds a display name into a valid {@link ResourceLocation} path — the same "sanitize, don't reject" convention {@code MaterialData#textureKeyFor} already uses for a model's own path. */
    private static String sanitize(String raw) {
        StringBuilder sanitized = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            sanitized.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/' ? c : '_');
        }
        return sanitized.length() == 0 ? "npc" : sanitized.toString();
    }
}
