package com.dimalab.storymodengine.common.scripting.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.cinematic.registry.CutsceneRegistry;
import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.common.dialogue.DialogueSystem;
import com.dimalab.storymodengine.common.dialogue.registry.DialogueRegistry;
import com.dimalab.storymodengine.common.entity.npc.NpcDefinition;
import com.dimalab.storymodengine.common.entity.npc.NpcDefinitionRegistry;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.common.quest.QuestSystem;
import com.dimalab.storymodengine.common.scripting.ast.NpcDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.SmeNode;
import com.dimalab.storymodengine.common.scripting.ast.SmeValueType;
import com.dimalab.storymodengine.common.scripting.compiler.SmeCompiler;
import com.dimalab.storymodengine.common.scripting.diagnostics.Severity;
import com.dimalab.storymodengine.common.scripting.diagnostics.SmeDiagnostic;
import com.dimalab.storymodengine.common.scripting.lexer.LexResult;
import com.dimalab.storymodengine.common.scripting.lexer.SmeLexer;
import com.dimalab.storymodengine.common.scripting.parser.ParseResult;
import com.dimalab.storymodengine.common.scripting.parser.SmeParser;
import com.dimalab.storymodengine.common.scripting.persistence.StoryVariableStore;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import com.dimalab.storymodengine.common.scripting.registry.SmeSourceRegistry;
import com.dimalab.storymodengine.common.scripting.validation.SmeValidator;
import com.dimalab.storymodengine.common.scripting.validation.StoryValidationResult;
import com.dimalab.storymodengine.common.trigger.TriggerRegistry;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code /sme script test} — this project has no JUnit setup, so this is the same
 * in-game, assert-and-report substitute every other subsystem's own {@code test}/{@code selftest}
 * command uses (see {@code quest.debug.QuestCompilerTestCommand}). Two independent tiers: {@link
 * #testPipeline} (pure JVM — lexer/parser/validator against hand-authored snippets, no player
 * needed) and {@link #testIntegration} (needs the invoking player — proves the shipped {@code
 * prologue.sme} actually registered real content and that its dialogue/choice/variable/quest wiring
 * behaves correctly at runtime; it deliberately does not re-test Dialogue/Quest/Flow's own internals,
 * which already have their own coverage elsewhere).
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class ScriptTestCommand {

    private ScriptTestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("script")
                        .then(Commands.literal("test").executes(ScriptTestCommand::run))));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> failures = new ArrayList<>();

        runPipelineTestsIsolated(failures);
        testIntegration(player, failures);

        if (failures.isEmpty()) {
            EngineLog.channel("SME").success("script test: all checks passed");
            context.getSource().sendSuccess(() -> Component.literal("[SME] script test: all checks passed"), false);
        } else {
            String joined = String.join("; ", failures);
            EngineLog.channel("SME").error("script test: {} failure(s): {}", failures.size(), joined);
            context.getSource().sendFailure(Component.literal(
                    "[SME] script test: " + failures.size() + " failure(s) — see log"));
        }
        return failures.isEmpty() ? 1 : 0;
    }

    // ---------- Tier 1: pure JVM (lexer/parser/validator) ----------

    /**
     * {@link SmeSourceRegistry} is a live, process-global singleton — the same instance the running
     * server's real {@code .sme} reloads already populated (with {@code prologue.sme}'s ids, under
     * that file's own real identity string). Re-validating hand-authored snippets directly against
     * that live state would misreport every id as a duplicate "already declared" by a different file
     * (see that registry's own class doc). Snapshot, clear, run the pure-JVM checks against a clean
     * slate, then restore — the live server's actual registry is untouched by the time this returns.
     */
    private static void runPipelineTestsIsolated(List<String> failures) {
        var snapshot = SmeSourceRegistry.snapshot();
        SmeSourceRegistry.reset();
        try {
            testPipeline(failures);
        } finally {
            SmeSourceRegistry.restore(snapshot);
        }
    }

    private static void testPipeline(List<String> failures) {
        checkValid(failures, "minimal valid story", """
                story t_minimal {
                    var x = 0
                    trigger t1 {
                        when time 1000
                        run { set x += 1 }
                    }
                }
                """);

        checkValid(failures, "negative number literal in a var declaration", """
                story t_neg_var {
                    var y = -59
                    var height = -1.5
                }
                """);

        checkValid(failures, "negative number literal as an action-call argument", """
                story t_neg_arg {
                    sequence s1 { give player "diamond" -5 }
                }
                """);

        // Regression: two plain action-call statements, each on its own line, with nothing between
        // them (no 'wait', no keyword statement). There is no statement separator in this grammar, so
        // an earlier version's argument-list loop kept consuming tokens past the end of the first
        // call — swallowing the second call's own name and arguments as if they belonged to the
        // first — until it happened to hit a reserved keyword. Caught for real the first time a real
        // .sme file chained two non-built-in commands with no 'wait' between them (see
        // SmeParser#sameLineAsPreviousToken).
        checkValid(failures, "two consecutive action-call statements with no separator", """
                story t_consecutive_actions {
                    sequence s1 {
                        give player "diamond" 5
                        teleport player "spawn"
                    }
                }
                """);

        checkDiagnosticCode(failures, "duplicate dialogue id", "SME201", """
                story t_dup_a { dialogue guard_intro { Guard: "hi" end } }
                story t_dup_b { dialogue guard_intro { Guard: "hi" end } }
                """);

        checkDiagnosticCode(failures, "undefined variable read", "SME203", """
                story t_undef_var {
                    sequence s1 { if (missing_var) { end } }
                }
                """);

        checkDiagnosticCode(failures, "unknown quest reference", "SME303", """
                story t_bad_ref {
                    sequence s1 { start quest does_not_exist }
                }
                """);

        checkDiagnosticCode(failures, "unresolvable dialogue jump target", "SME300", """
                story t_bad_jump {
                    dialogue d1 { Guard: "hi" jump nowhere }
                }
                """);

        checkDiagnosticCode(failures, "bad arg type to built-in command", "SME402", """
                story t_bad_arg {
                    sequence s1 { give player "not_a_number" "also_not_a_number" }
                }
                """);

        checkBundledResourceParses(failures);
        testNpcDeclarationCompiles(failures);
    }

    /**
     * Exercises the whole {@code npc { }} pipeline end to end — lex, parse, validate, compile,
     * registered — the same real path {@code SmeReloadListener} uses, not a shortcut. Registers into
     * the live, process-global {@code NpcDefinitionRegistry}/{@code FlowRegistry} under a name no real
     * content would plausibly use; unlike the pure-JVM tier's other checks this isn't snapshotted and
     * restored (that machinery exists specifically for id-uniqueness diagnostics, which {@code
     * NpcDefinitionRegistry} doesn't have — a same-named redeclare just warns and overwrites), so a
     * server restart is what clears this, same as any other test data a debug command might leave.
     */
    private static void testNpcDeclarationCompiles(List<String> failures) {
        String source = """
                story t_npc_selftest {
                    npc "SelfTestNpc" {
                        model "storymodengine/models/player_model"
                        behavior stationary
                        pos 1.0 -2.5 3.0
                        onInteract { }
                    }
                }
                """;
        LexResult lex = SmeLexer.lex("npc-selftest", source);
        ParseResult parsed = SmeParser.parse("npc-selftest", lex.tokens());
        if (!lex.diagnostics().isEmpty() || !parsed.diagnostics().isEmpty()) {
            failures.add("npc declaration: unexpected lex/parse diagnostics — " + lex.diagnostics() + parsed.diagnostics());
            return;
        }
        if (parsed.stories().size() != 1 || parsed.stories().get(0).declarations().size() != 1) {
            failures.add("npc declaration: expected exactly one story with one declaration");
            return;
        }
        SmeNode decl = parsed.stories().get(0).declarations().get(0);
        if (!(decl instanceof NpcDeclNode npcDecl)) {
            failures.add("npc declaration: expected an NpcDeclNode, got " + decl.getClass());
            return;
        }
        if (!"SelfTestNpc".equals(npcDecl.name()) || !"storymodengine/models/player_model".equals(npcDecl.model())
                || npcDecl.x() != 1.0 || npcDecl.y() != -2.5 || npcDecl.z() != 3.0) {
            failures.add("npc declaration: fields did not parse correctly — " + npcDecl);
            return;
        }

        SmeSourceRegistry.claimAll("npc-selftest", parsed.stories().get(0));
        for (StoryValidationResult result : SmeValidator.validate("npc-selftest", parsed.stories())) {
            if (result.hasErrors()) {
                failures.add("npc declaration: validation reported errors — " + result.diagnostics());
                return;
            }
            SmeCompiler.compile("npc-selftest", result.story(), result.varTypes());
        }

        NpcDefinition definition = NpcDefinitionRegistry.get("SelfTestNpc");
        if (definition == null) {
            failures.add("npc declaration: NpcDefinition was not registered under its name");
            return;
        }
        if (definition.behavior() != NpcBehavior.STATIONARY) {
            failures.add("npc declaration: expected STATIONARY behavior, got " + definition.behavior());
        }
        if (definition.onInteractFlowId() == null) {
            failures.add("npc declaration: a declared onInteract {} (even empty) should still register a Flow id");
        }
    }

    private static void checkValid(List<String> failures, String label, String source) {
        var result = compileString(label, source);
        long errors = result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).count();
        if (errors > 0) {
            failures.add(label + ": expected 0 errors, got " + errors + " — " + result.diagnostics());
        }
    }

    private static void checkDiagnosticCode(List<String> failures, String label, String expectedCode, String source) {
        var result = compileString(label, source);
        boolean found = result.diagnostics().stream().anyMatch(d -> d.code().equals(expectedCode));
        if (!found) {
            failures.add(label + ": expected diagnostic " + expectedCode + ", got " + result.diagnostics());
        }
    }

    private static void checkBundledResourceParses(List<String> failures) {
        String source = readBundledPrologue();
        if (source == null) {
            failures.add("prologue.sme: could not read bundled resource");
            return;
        }
        var result = compileString("prologue.sme", source);
        long errors = result.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).count();
        if (errors > 0) {
            failures.add("prologue.sme: expected 0 errors, got " + errors + " — " + result.diagnostics());
        }
        if (result.stories().isEmpty()) {
            failures.add("prologue.sme: parsed zero stories");
        }
    }

    private static String readBundledPrologue() {
        try (InputStream in = ScriptTestCommand.class.getResourceAsStream(
                "/data/storymodengine/storymodengine/stories/prologue.sme")) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private record CompileCheckResult(List<com.dimalab.storymodengine.common.scripting.ast.StoryNode> stories, List<SmeDiagnostic> diagnostics) {
    }

    private static CompileCheckResult compileString(String label, String source) {
        LexResult lex = SmeLexer.lex(label, source);
        ParseResult parsed = SmeParser.parse(label, lex.tokens());
        List<SmeDiagnostic> all = new ArrayList<>(lex.diagnostics());
        all.addAll(parsed.diagnostics());
        // Mirrors SmeReloadListener's own claim-before-validate protocol for this one snippet, since
        // ReferenceResolutionPass/DuplicateIdPass both read SmeSourceRegistry rather than the raw AST.
        for (var story : parsed.stories()) {
            SmeSourceRegistry.claimAll(label, story);
        }
        for (StoryValidationResult r : SmeValidator.validate(label, parsed.stories())) {
            all.addAll(r.diagnostics());
        }
        return new CompileCheckResult(parsed.stories(), all);
    }

    // ---------- Tier 2: live integration (needs the invoking player) ----------

    private static void testIntegration(ServerPlayer player, List<String> failures) {
        testNpcLookupRoundTrip(player, failures);

        checkNotNull(failures, "guard_intro registered", DialogueRegistry.get(ScriptingRegistryFacade.storyId("guard_intro")));
        checkNotNull(failures, "guard_welcome registered", DialogueRegistry.get(ScriptingRegistryFacade.storyId("guard_welcome")));
        checkNotNull(failures, "village_trial registered", QuestRegistry.get(ScriptingRegistryFacade.storyId("village_trial")));
        checkNotNull(failures, "village_entry registered", TriggerRegistry.get(ScriptingRegistryFacade.storyId("village_entry")));
        checkNotNull(failures, "trial_complete registered", TriggerRegistry.get(ScriptingRegistryFacade.storyId("trial_complete")));
        checkNotNull(failures, "village_welcome cutscene registered", CutsceneRegistry.get(ScriptingRegistryFacade.storyId("village_welcome")));

        var guardIntro = DialogueRegistry.get(ScriptingRegistryFacade.storyId("guard_intro"));
        if (guardIntro == null) {
            failures.add("guard_intro: cannot continue integration test, dialogue missing");
            return;
        }

        DialogueSystem.start(player, guardIntro);
        if (DialogueSystem.getActive(player) == null) {
            failures.add("guard_intro: did not start (DialogueSystem.getActive == null)");
            return;
        }

        var dialogueId = ScriptingRegistryFacade.storyId("guard_intro");

        // Every DialogueLine compiles to show-line + a one-option "continue" Flow.choice (see
        // DialogueRunner) — the choice group after the opening line only becomes selectable once
        // that's been acknowledged; selecting "choice_1" before this always returns false.
        boolean continued = DialogueSystem.selectChoice(player, dialogueId, "continue");
        if (!continued) {
            failures.add("guard_intro: selectChoice('continue') past the opening line returned false");
            return;
        }

        long reputationBefore = StoryVariableStore.get(player, "village.reputation", SmeValueType.INT).intVal();

        boolean selected = DialogueSystem.selectChoice(player, dialogueId, "choice_1");
        if (!selected) {
            failures.add("guard_intro: selectChoice('choice_1') returned false");
        }

        // A relative delta, not an absolute value: this is a live, persistent server — a previous
        // playthrough or a prior run of this same command may have already changed the value.
        long reputationAfter = StoryVariableStore.get(player, "village.reputation", SmeValueType.INT).intVal();
        checkEquals(failures, "village.reputation change after choosing 'I come in peace.'", 1L, reputationAfter - reputationBefore);

        var questId = ScriptingRegistryFacade.storyId("village_trial");
        boolean started = QuestSystem.isActive(player, questId) || QuestSystem.isCompleted(player, questId);
        checkEquals(failures, "village_trial started (active or already completed) after choice", true, started);
    }

    /**
     * A throwaway {@code NpcEntity}, spawned and discarded within this one check — {@link
     * com.dimalab.storymodengine.common.entity.npc.NpcLookup}'s registered-index path and its
     * self-healing full-level-scan fallback both need a real live entity to resolve against, which
     * the pure-JVM tier above cannot provide. Also exercises {@code npc_set_pickup} directly, since
     * it needs no world/render context beyond the entity itself — catching a wrong-signature or
     * wrong-field mistake mechanically rather than only by testing in-game.
     */
    private static void testNpcLookupRoundTrip(ServerPlayer player, List<String> failures) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        String name = "SelfTestLookupNpc";
        com.dimalab.storymodengine.common.entity.npc.NpcEntity npc =
                com.dimalab.storymodengine.common.entity.ModEntities.NPC.get().create(level);
        if (npc == null) {
            failures.add("npc lookup: could not create a throwaway NpcEntity");
            return;
        }
        try {
            npc.moveTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
            npc.setScriptName(name);
            level.addFreshEntity(npc);

            com.dimalab.storymodengine.common.entity.npc.NpcLookup.register(level, name, npc.getUUID());
            var resolved = com.dimalab.storymodengine.common.entity.npc.NpcLookup.resolve(level, name);
            checkEquals(failures, "npc lookup: resolve() returns the registered entity",
                    npc.getUUID(), resolved == null ? null : resolved.getUUID());

            // Self-healing fallback: drop the index entry, confirm a full-level scan by scriptName
            // still finds the (still-live) entity and repairs the index — NpcLookup's whole point.
            com.dimalab.storymodengine.common.entity.npc.NpcLookup.unregister(level, name);
            var rescanned = com.dimalab.storymodengine.common.entity.npc.NpcLookup.resolve(level, name);
            checkEquals(failures, "npc lookup: unindexed-but-live scan fallback finds the entity",
                    npc.getUUID(), rescanned == null ? null : rescanned.getUUID());

            npc.setCanPickUpLoot(false);
            npc.setCanPickUpLoot(true);
            checkEquals(failures, "npc_set_pickup: setCanPickUpLoot(true) sticks", true, npc.canPickUpLoot());
        } finally {
            com.dimalab.storymodengine.common.entity.npc.NpcLookup.unregister(level, name);
            npc.discard();
        }
    }

    private static void checkNotNull(List<String> failures, String label, Object value) {
        if (value == null) {
            failures.add(label + ": expected non-null, got null");
        }
    }

    private static void checkEquals(List<String> failures, String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            failures.add(label + ": expected " + expected + ", got " + actual);
        }
    }
}
