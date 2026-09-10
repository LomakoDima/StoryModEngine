package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.event.Event;
import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.entity.EntityTargeting;
import com.dimalab.storymodengine.common.entity.data.EntityDataAccess;
import com.dimalab.storymodengine.common.entity.npc.NpcDestroyBlockTask;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import com.dimalab.storymodengine.common.entity.npc.NpcLookup;
import com.dimalab.storymodengine.common.entity.npc.NpcMoveToTask;
import com.dimalab.storymodengine.common.entity.search.AwaitEntityTask;
import com.dimalab.storymodengine.common.entity.search.EntitySearch;
import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.flow.Scope;
import com.dimalab.storymodengine.common.flow.wait.PersistentWaitTask;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.QuestSystem;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.command.StoryCommandRegistry;
import com.dimalab.storymodengine.common.scripting.persistence.SmeValue;
import com.dimalab.storymodengine.common.scripting.persistence.StoryVariableStore;
import com.dimalab.storymodengine.common.scripting.registry.EventNameRegistry;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code List<StmtNode>} → {@link Flow}, reused verbatim for a top-level {@code sequence <id> {}}
 * declaration, a {@code trigger { run {} }} body, and (indirectly, via {@link EffectCompiler}'s
 * {@code dctx.flowContext()} bridge) a dialogue action/choice body's non-suspending statements.
 * Every statement lowers to exactly one {@link Flow} step, joined with {@code Flow.sequence(...)}
 * (verified, {@code Flow.java:92}); {@code wait}/{@code wait event}/{@code call} are the only
 * statements that need a real suspending {@code Flow} primitive rather than a plain {@code
 * Flow.action} wrapping a synchronous effect.
 */
public final class SequenceCompiler {

    private final ExprCompiler exprCompiler;
    private final EffectCompiler effectCompiler;

    public SequenceCompiler(CompileContext ctx) {
        this.exprCompiler = new ExprCompiler(ctx);
        this.effectCompiler = new EffectCompiler(ctx);
    }

    public Flow compile(List<StmtNode> stmts) {
        List<Flow> steps = new ArrayList<>(stmts.size());
        for (StmtNode stmt : stmts) {
            steps.add(compileOne(stmt));
        }
        return Flow.sequence(steps.toArray(new Flow[0]));
    }

    private Flow compileOne(StmtNode stmt) {
        if (stmt instanceof WaitStmtNode w) {
            return Flow.wait(w.ticks());
        }
        if (stmt instanceof WaitEventStmtNode w) {
            return waitForNamedEvent(w.eventName());
        }
        if (stmt instanceof CallStmtNode c) {
            String id = c.sequenceId();
            return Flow.subFlow(Flow.lazy(() -> ScriptingRegistryFacade.sequence(id)));
        }
        if (stmt instanceof ReturnStmtNode) {
            return Flow.actionResult(fc -> false);
        }
        if (stmt instanceof EndStmtNode) {
            return Flow.action(fc -> {
            });
        }
        if (stmt instanceof IfStmtNode ifStmt) {
            return compileIf(ifStmt);
        }
        if (stmt instanceof RaycastIfStmtNode r) {
            Evaluator<Boolean> cond = RaycastCompiler.compileCondition(r);
            Flow thenFlow = compile(r.thenBlock());
            Flow elseFlow = compile(r.elseBlock());
            return Flow.branch(cond, thenFlow, elseFlow);
        }
        if (stmt instanceof JumpStmtNode j) {
            String id = j.targetId();
            return Flow.action(fc -> startById(fc, id));
        }
        if (stmt instanceof ActionCallStmtNode call) {
            Flow special = compileSpecialForm(call);
            if (special != null) {
                return special;
            }
            // Every special form below falls through here only when its own @StoryCommand wasn't
            // registered (should never happen once ScriptingBootstrap has run) — reproduces the same
            // "unknown command" error ActionCallCompiler.compile logs, exactly like npc_move_to did.
        }
        // Set/VarDecl/StartQuest/StartDialogue/PlayCinematic/ActionCall — a plain synchronous
        // effect, reusing EffectCompiler rather than a second implementation of the same mapping.
        Consumer<FlowContext> effect = effectCompiler.compile(List.of(stmt));
        return Flow.action(effect::accept);
    }

    /**
     * Every statement that needs to bind a result into a script variable (something a plain {@code
     * void @StoryCommand} can't do on its own) or suspend on a {@link com.dimalab.storymodengine.common.flow.Task}
     * gets special-cased here, one {@code if} per command name — the same shape {@code
     * compileAwaitableMoveTo} already established for {@code npc_move_to}. Returns {@code null} only
     * if the command name doesn't match any special form <b>or</b> its backing {@code @StoryCommand}
     * wasn't registered; the caller falls through to the generic {@code ActionCall} path either way.
     */
    private Flow compileSpecialForm(ActionCallStmtNode call) {
        return switch (call.commandName()) {
            case "npc_move_to" -> compileAwaitableMoveTo(call);
            case "npc_break_block" -> compileAwaitableBreakBlock(call);
            case "find_nearest_entity" -> compileFindNearestEntity(call);
            case "await_entity" -> compileAwaitEntity(call);
            case "entity_get_data_string" -> compileEntityGetData(call, true);
            case "entity_get_data_int" -> compileEntityGetData(call, false);
            case "entity_get_or_set_data_string" -> compileEntityGetOrSetData(call);
            case "wait_persistent" -> compileWaitPersistent(call);
            default -> null;
        };
    }

    /** {@code find_nearest_entity <resultVar> <entityTypeId> <radius>} — a synchronous radius scan centered on the triggering player, binding a UUID string (or "" for no match) since that's the only carrier a plain script STRING variable offers. */
    private Flow compileFindNearestEntity(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String resultVar = (String) coerced.get(0);
        String entityTypeId = (String) coerced.get(1);
        double radius = (Double) coerced.get(2);
        return Flow.action(fc -> {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(parseId(entityTypeId));
            UUID found = type != null
                    ? EntitySearch.findNearest(fc.player().serverLevel(), fc.player().position(), type, radius)
                    : null;
            StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofString(found != null ? found.toString() : ""));
        });
    }

    /** {@code await_entity <targetVar> <timeoutTicks>} — suspends until the UUID currently held by {@code targetVar} resolves to a loaded, alive entity, or the timeout elapses. See {@link AwaitEntityTask}. */
    private Flow compileAwaitEntity(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String targetVar = (String) coerced.get(0);
        int timeoutTicks = (Integer) coerced.get(1);
        return Flow.task(() -> new AwaitEntityTask(targetVar, timeoutTicks));
    }

    /** {@code entity_get_data_string/int <resultVar> <target> <key>} — a synchronous read across all three {@code entity.data} sync tiers (see {@code EntityDataAccess.tagContaining}). */
    private Flow compileEntityGetData(ActionCallStmtNode call, boolean asString) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String resultVar = (String) coerced.get(0);
        String target = (String) coerced.get(1);
        String key = (String) coerced.get(2);
        return Flow.action(fc -> {
            Entity entity = EntityTargeting.resolve(fc.player().serverLevel(), target);
            CompoundTag tag = entity != null ? EntityDataAccess.tagContaining(entity, key) : null;
            if (asString) {
                StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofString(tag != null ? tag.getString(key) : ""));
            } else {
                StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofInt(tag != null ? tag.getInt(key) : 0));
            }
        });
    }

    /** {@code entity_get_or_set_data_string <resultVar> <target> <key> <defaultValue>} — SME's scoped stand-in for HollowEngine's {@code remember} (no closures in this grammar, so only a fixed default, not arbitrary computation). */
    private Flow compileEntityGetOrSetData(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String resultVar = (String) coerced.get(0);
        String target = (String) coerced.get(1);
        String key = (String) coerced.get(2);
        String defaultValue = (String) coerced.get(3);
        return Flow.action(fc -> {
            Entity entity = EntityTargeting.resolve(fc.player().serverLevel(), target);
            if (entity == null) {
                StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofString(defaultValue));
                return;
            }
            CompoundTag existing = EntityDataAccess.tagContaining(entity, key);
            if (existing != null) {
                StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofString(existing.getString(key)));
                return;
            }
            EntityDataAccess.tagFor(entity, "never").putString(key, defaultValue);
            EntityDataAccess.markAndSync(entity, "never");
            StoryVariableStore.set(fc.player(), resultVar, SmeValue.ofString(defaultValue));
        });
    }

    /** {@code wait_persistent <name> <ticks>} — see {@link PersistentWaitTask}. */
    private Flow compileWaitPersistent(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String name = (String) coerced.get(0);
        int ticks = (Integer) coerced.get(1);
        return Flow.task(() -> new PersistentWaitTask(name, ticks));
    }

    /** Same "bare id defaults to minecraft:" convention {@code BuiltinStoryCommands}/{@code NpcScriptCommands} already use for item/dimension ids. */
    private static ResourceLocation parseId(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    /**
     * {@code npc_move_to} called from a sequence/trigger body (not a dialogue action/choice body —
     * those go through {@code EffectCompiler} instead, which never reaches {@code compileOne} at all,
     * so they keep the plain fire-and-forget behavior unchanged) compiles to two steps instead of one
     * bare {@code Flow.action}: run the command exactly as {@link ActionCallCompiler#compile} would,
     * then suspend on {@link NpcMoveToTask} until the NPC arrives, gets stuck, or is superseded by a
     * newer MOVEMENT-channel command (see {@code NpcEntity#beginMovementChannel}).
     *
     * <p>The captured movement generation has to travel through the flow instance's own {@code
     * Blackboard} ({@link Scope#INSTANCE}), not a captured Java variable — a {@link Flow} is an
     * immutable recipe that can be {@code instantiate()}d concurrently (two triggers moving the same
     * or different NPCs at once), so anything that varies per run must live in per-instance {@code
     * FlowContext} state, not a closure shared across every instantiation. {@code npcName}/{@code
     * target}, by contrast, are compile-time constants straight from the script's own literal
     * arguments (this grammar allows no other kind — see {@link ActionCallCompiler}'s own doc) and are
     * safe to capture directly, the same way {@code ActionCallCompiler.compile} already captures its
     * own {@code coerced} list.
     */
    private Flow compileAwaitableMoveTo(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String commandName = call.commandName();
        String npcName = (String) coerced.get(0);
        Vec3 target = new Vec3((double) coerced.get(1), (double) coerced.get(2), (double) coerced.get(3));

        Flow start = Flow.action(fc -> {
            StoryCommandRegistry.invoke(commandName, fc.player(), coerced);
            NpcEntity npc = NpcLookup.resolve(fc.player().serverLevel(), npcName);
            fc.setVariable(Scope.INSTANCE, NpcMoveToTask.GENERATION_KEY, npc != null ? npc.movementGeneration() : -1L);
        });
        return Flow.sequence(start, Flow.task(() -> new NpcMoveToTask(npcName, target)));
    }

    /**
     * {@code npc_break_block} called from a sequence/trigger body — same two-step shape as {@link
     * #compileAwaitableMoveTo}: run the command (which itself calls {@code beginMovementChannel()} and
     * bumps {@code movementGeneration}), capture that generation under {@link NpcMoveToTask#GENERATION_KEY}
     * (the two commands share one "movement channel," so either superseding the other should cancel this
     * wait the same way), then await {@link NpcDestroyBlockTask} until the goal clears the target.
     */
    private Flow compileAwaitableBreakBlock(ActionCallStmtNode call) {
        var handle = StoryCommandRegistry.get(call.commandName());
        if (handle == null) {
            return null;
        }
        List<Object> coerced = ActionCallCompiler.coerceArgs(call, handle);
        String commandName = call.commandName();
        String npcName = (String) coerced.get(0);

        Flow start = Flow.action(fc -> {
            StoryCommandRegistry.invoke(commandName, fc.player(), coerced);
            NpcEntity npc = NpcLookup.resolve(fc.player().serverLevel(), npcName);
            fc.setVariable(Scope.INSTANCE, NpcMoveToTask.GENERATION_KEY, npc != null ? npc.movementGeneration() : -1L);
        });
        return Flow.sequence(start, Flow.task(() -> new NpcDestroyBlockTask(npcName)));
    }

    private Flow compileIf(IfStmtNode ifStmt) {
        Flow elseFlow = compile(ifStmt.elseBlock());
        for (int i = ifStmt.elseIfs().size() - 1; i >= 0; i--) {
            ElseIfBranch b = ifStmt.elseIfs().get(i);
            elseFlow = Flow.branch(exprCompiler.compileBoolean(b.cond()), compile(b.block()), elseFlow);
        }
        return Flow.branch(exprCompiler.compileBoolean(ifStmt.cond()), compile(ifStmt.thenBlock()), elseFlow);
    }

    @SuppressWarnings("unchecked")
    private static Flow waitForNamedEvent(String eventName) {
        Class<? extends Event> type = EventNameRegistry.classFor(eventName);
        Function<Event, net.minecraft.server.level.ServerPlayer> extractor = EventNameRegistry.playerExtractorFor(eventName);
        if (type == null) {
            EngineLog.channel("SME").error("wait event: unknown event name '{}' (should have failed validation)", eventName);
            return Flow.action(fc -> {
            });
        }
        return Flow.waitForEvent((Class<Event>) type, (FlowContext fc, Event e) -> {
            var player = extractor.apply(e);
            return player != null && player.equals(fc.player());
        });
    }

    /** {@code jump <id>} at sequence/trigger scope — resolves against dialogue → quest → cutscene → sequence, first match wins (pinned at compile time by {@code validation.ReferenceResolutionPass}, so this is never ambiguous by the time it runs). */
    private static void startById(FlowContext fc, String id) {
        var dialogue = ScriptingRegistryFacade.dialogue(id);
        if (dialogue != null) {
            DialogueSystem.start(fc.player(), dialogue);
            return;
        }
        if (ScriptingRegistryFacade.quest(id) != null) {
            QuestSystem.start(fc.player(), ScriptingRegistryFacade.storyId(id));
            return;
        }
        var cutscene = ScriptingRegistryFacade.cutscene(id);
        if (cutscene != null) {
            CinematicManager.play(fc.player(), cutscene, Map.of());
            return;
        }
        if (ScriptingRegistryFacade.sequence(id) != null) {
            FlowManager.start(ScriptingRegistryFacade.storyId(id), fc.player());
            return;
        }
        EngineLog.channel("SME").error("jump: '{}' does not match any dialogue, quest, cutscene, or sequence (should have failed validation)", id);
    }
}
