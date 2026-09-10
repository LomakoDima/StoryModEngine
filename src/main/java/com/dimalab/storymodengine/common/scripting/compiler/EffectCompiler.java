package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.flow.Evaluator;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.common.cinematic.CinematicManager;
import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.quest.QuestSystem;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.persistence.SmeValue;
import com.dimalab.storymodengine.common.scripting.persistence.StoryVariableStore;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compiles a <em>flat, synchronous</em> {@code List<StmtNode>} into one {@code Consumer<FlowContext>}
 * — used only for a dialogue choice/action body, which runs inside a single {@code
 * DialogueCommand.execute(DialogueContext)} call and cannot suspend. {@code
 * validation.ReferenceResolutionPass} already rejects {@code wait}/{@code wait event}/{@code call}/
 * {@code return} anywhere inside a dialogue body (would need real {@code Flow} suspension), so this
 * compiler never has to handle them. {@code jump}/{@code end} are handled separately by {@link
 * DialogueCompiler} (as a trailing {@code .gotoNode(...)} call), not as a runtime effect here.
 *
 * <p>{@code DialogueContext.flowContext()} (verified accessor) is what lets this reuse the exact
 * same {@code Consumer<FlowContext>}-shaped effects {@link SequenceCompiler} builds for {@code
 * Flow.action(...)} — a dialogue's {@code DialogueCommand.of(dctx -> compiled.accept(dctx.flowContext()))}
 * is the one bridging line, not a second, parallel effect system.
 */
public final class EffectCompiler {

    private final ExprCompiler exprCompiler;

    public EffectCompiler(CompileContext ctx) {
        this.exprCompiler = new ExprCompiler(ctx);
    }

    public Consumer<FlowContext> compile(List<StmtNode> stmts) {
        List<Consumer<FlowContext>> effects = new ArrayList<>();
        for (StmtNode stmt : stmts) {
            effects.add(compileOne(stmt));
        }
        return fc -> effects.forEach(e -> e.accept(fc));
    }

    private Consumer<FlowContext> compileOne(StmtNode stmt) {
        if (stmt instanceof SetStmtNode set) {
            return compileSet(set);
        }
        if (stmt instanceof VarDeclNode) {
            return fc -> {
            };
        }
        if (stmt instanceof IfStmtNode ifStmt) {
            return compileIf(ifStmt);
        }
        if (stmt instanceof RaycastIfStmtNode raycast) {
            Evaluator<Boolean> cond = RaycastCompiler.compileCondition(raycast);
            Consumer<FlowContext> thenEffect = compile(raycast.thenBlock());
            Consumer<FlowContext> elseEffect = compile(raycast.elseBlock());
            return fc -> (Boolean.TRUE.equals(cond.evaluate(fc)) ? thenEffect : elseEffect).accept(fc);
        }
        if (stmt instanceof StartQuestStmtNode s) {
            String id = s.questId();
            return fc -> QuestSystem.start(fc.player(), ScriptingRegistryFacade.storyId(id));
        }
        if (stmt instanceof StartDialogueStmtNode s) {
            String id = s.dialogueId();
            return fc -> {
                var def = ScriptingRegistryFacade.dialogue(id);
                if (def != null) {
                    DialogueSystem.start(fc.player(), def);
                }
            };
        }
        if (stmt instanceof PlayCinematicStmtNode s) {
            String id = s.cutsceneId();
            return fc -> {
                var def = CutsceneRegistry.get(ScriptingRegistryFacade.storyId(id));
                if (def != null) {
                    CinematicManager.play(fc.player(), def, java.util.Map.of());
                }
            };
        }
        if (stmt instanceof ActionCallStmtNode call) {
            return ActionCallCompiler.compile(call);
        }
        // JumpStmtNode/EndStmtNode: handled by DialogueCompiler's own trailing-jump/end detection,
        // never reached as a runtime effect (see that class's choice-body compilation).
        return fc -> {
        };
    }

    private Consumer<FlowContext> compileIf(IfStmtNode ifStmt) {
        record Branch(Evaluator<Boolean> cond, Consumer<FlowContext> effect) {
        }
        List<Branch> branches = new ArrayList<>();
        branches.add(new Branch(exprCompiler.compileBoolean(ifStmt.cond()), compile(ifStmt.thenBlock())));
        for (ElseIfBranch b : ifStmt.elseIfs()) {
            branches.add(new Branch(exprCompiler.compileBoolean(b.cond()), compile(b.block())));
        }
        Consumer<FlowContext> elseEffect = compile(ifStmt.elseBlock());
        return fc -> {
            for (Branch b : branches) {
                if (Boolean.TRUE.equals(b.cond().evaluate(fc))) {
                    b.effect().accept(fc);
                    return;
                }
            }
            elseEffect.accept(fc);
        };
    }

    private Consumer<FlowContext> compileSet(SetStmtNode set) {
        String name = set.varName();
        Evaluator<Object> value = exprCompiler.compileValue(set.value());
        return switch (set.op()) {
            case ASSIGN -> fc -> StoryVariableStore.set(fc.player(), name, toSmeValue(value.evaluate(fc)));
            case ADD_ASSIGN -> fc -> StoryVariableStore.add(fc.player(), name, toSmeValue(value.evaluate(fc)));
            case SUB_ASSIGN -> fc -> StoryVariableStore.subtract(fc.player(), name, toSmeValue(value.evaluate(fc)));
        };
    }

    static SmeValue toSmeValue(Object v) {
        if (v instanceof Boolean b) {
            return SmeValue.ofBool(b);
        }
        if (v instanceof Long l) {
            return SmeValue.ofInt(l);
        }
        if (v instanceof Double d) {
            return SmeValue.ofDouble(d);
        }
        return SmeValue.ofString(String.valueOf(v));
    }
}
