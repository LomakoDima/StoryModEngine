package com.dimalab.storymodengine.common.dialogue;

import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Scope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * The bridge a {@link DialogueCommand}/condition actually runs against — wraps a {@link
 * FlowContext} rather than replacing it, so Blackboard access ({@link #getVariable}/{@link
 * #setVariable}) is the exact same mechanism a plain {@code Flow} already uses, not a second
 * variable system. Adds only what's genuinely dialogue-specific: {@link #bind}/{@link
 * #resolveActor} (a plain speaker-name → entity-id map, not the generic {@code
 * cinematic.binding.Binding<T>} interface — that's typed to {@code CutsceneContext} specifically,
 * and a dialogue's binding need is simple enough that reusing it would mean introducing a second
 * abstraction just to satisfy a type parameter) and {@link #close}.
 *
 * <p>A {@code DialogueDefinition} never stores an entity id itself — bindings are supplied by
 * whatever starts the dialogue (a command, a quest step, an NPC's own interaction handler) and live
 * only on this per-run context, mirroring how {@code CutsceneContext} never lets a {@code
 * CutsceneDefinition} hold a live entity reference either.
 */
public final class DialogueContext {

    private final FlowContext flowContext;
    private final ResourceLocation dialogueId;
    private final Map<String, Integer> actorEntityIds = new HashMap<>();

    DialogueContext(FlowContext flowContext, ResourceLocation dialogueId) {
        this.flowContext = flowContext;
        this.dialogueId = dialogueId;
    }

    public ServerPlayer player() {
        return flowContext.player();
    }

    public Level level() {
        return flowContext.level();
    }

    public FlowContext flowContext() {
        return flowContext;
    }

    public ResourceLocation dialogueId() {
        return dialogueId;
    }

    /** Associates a speaker/actor name (as referenced by {@link DialogueLine#speaker}) with a live entity for this run. */
    public void bind(String name, int entityId) {
        actorEntityIds.put(name, entityId);
    }

    /** The entity bound to {@code name}, or {@code null} if unbound or no longer present. */
    public Entity resolveActor(String name) {
        Integer entityId = actorEntityIds.get(name);
        if (entityId == null || level() == null) {
            return null;
        }
        return level().getEntity(entityId);
    }

    public <T> T getVariable(Scope scope, String key) {
        return flowContext.getVariable(scope, key);
    }

    public <T> void setVariable(Scope scope, String key, T value) {
        flowContext.setVariable(scope, key, value);
    }

    /** Ends this dialogue immediately, as if a {@link DialogueEnd} entry had been reached. */
    public void close() {
        DialogueSystem.stop(player());
    }
}
