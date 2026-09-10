package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.EventNameRegistry;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import com.dimalab.storymodengine.common.scripting.registry.SmeSourceRegistry;
import com.dimalab.storymodengine.common.scripting.registry.ZoneRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Every quest/dialogue/cutscene/sequence/event/zone reference in a file, resolved against the
 * reload-wide {@link SmeSourceRegistry} (same-batch {@code .sme} declarations, regardless of file
 * processing order — see that class's doc) union the live runtime registries (a dialogue/quest/
 * cutscene registered from Java or JSON, already loaded before this reload). A cross-reference that
 * resolves against neither is an {@code SME3xx} error; {@code play cinematic}/{@code start
 * dialogue}/{@code start quest} are never validated against {@code storyId}'s namespace-defaulted
 * self-file-only symbol table alone, since referencing an existing hand-written definition is
 * exactly as valid as referencing another {@code .sme} file's declaration.
 */
public final class ReferenceResolutionPass {

    private ReferenceResolutionPass() {
    }

    public static void run(ValidationContext ctx) {
        for (SmeNode decl : ctx.story().declarations()) {
            if (decl instanceof DialogueDeclNode d) {
                checkDialogue(ctx, d);
            } else if (decl instanceof QuestDeclNode q) {
                checkQuest(ctx, q);
            } else if (decl instanceof TriggerDeclNode t) {
                checkTrigger(ctx, t);
            } else if (decl instanceof SequenceDeclNode s) {
                StmtWalker.walk(s.body(), visitor(ctx));
            } else if (decl instanceof NpcDeclNode n) {
                StmtWalker.walk(n.onInteract(), visitor(ctx));
            }
        }
    }

    private static void checkDialogue(ValidationContext ctx, DialogueDeclNode d) {
        // A dedicated visitor, not the generic sequence/trigger one below: inside a dialogue body,
        // `jump <id>` compiles to `.gotoNode(id)` (see DialogueCompiler) and targets a sibling node
        // in *this* dialogue, never a cross-kind quest/cutscene/sequence reference — the one place
        // JumpStmtNode's meaning depends on where it appears (see SequenceCompiler's own note on the
        // same ambiguity for sequence/trigger scope).
        StmtWalker.Visitor dialogueVisitor = new StmtWalker.Visitor() {
            @Override
            public void visitStmt(StmtNode stmt) {
                if (stmt instanceof StartQuestStmtNode s) {
                    requireStoryRef(ctx, "quest", s.questId(), s.pos());
                } else if (stmt instanceof StartDialogueStmtNode s) {
                    requireStoryRef(ctx, "dialogue", s.dialogueId(), s.pos());
                } else if (stmt instanceof PlayCinematicStmtNode s) {
                    requireStoryRef(ctx, "cutscene", s.cutsceneId(), s.pos());
                } else if (stmt instanceof JumpStmtNode j) {
                    checkDialogueLocalTarget(ctx, d, j.targetId(), j.pos());
                } else if (stmt instanceof WaitStmtNode || stmt instanceof WaitEventStmtNode
                        || stmt instanceof CallStmtNode || stmt instanceof ReturnStmtNode) {
                    // A dialogue choice/action body compiles to a synchronous DialogueCommand
                    // (Consumer<DialogueContext>, see DialogueCompiler) — it cannot suspend, so
                    // anything that needs Flow-level suspension (wait/wait event/call a sub-flow) or
                    // Flow-sequence-abort semantics (return) has no meaning here. RaycastIfStmtNode
                    // is fine synchronously (an instant world query, no suspension) and stays allowed.
                    ctx.error(stmt.pos(), "SME305", stmt.getClass().getSimpleName()
                            + " is not supported inside a dialogue body — it runs synchronously and cannot suspend");
                }
            }
        };
        for (DialogueNodeNode node : d.nodes()) {
            for (DialogueEntryNode entry : node.entries()) {
                if (entry instanceof ActionEntryNode a) {
                    StmtWalker.walk(a.body(), dialogueVisitor);
                } else if (entry instanceof JumpEntryNode j) {
                    checkDialogueLocalTarget(ctx, d, j.targetNodeId(), j.pos());
                } else if (entry instanceof ChoiceGroupNode group) {
                    for (ChoiceNode choice : group.options()) {
                        StmtWalker.walk(choice.body(), dialogueVisitor);
                    }
                }
            }
        }
    }

    private static void checkDialogueLocalTarget(ValidationContext ctx, DialogueDeclNode d, String targetId, SourcePos pos) {
        boolean found = d.nodes().stream().anyMatch(n -> n.nodeId().equals(targetId));
        if (!found) {
            ctx.error(pos, "SME300", "Dialogue '" + d.id() + "' has no node named '" + targetId + "'");
        }
    }

    private static void checkQuest(ValidationContext ctx, QuestDeclNode q) {
        for (String prereq : q.prerequisites()) {
            requireStoryRef(ctx, "quest", prereq, q.pos());
        }
        for (ObjectiveNode obj : q.objectives()) {
            if (obj.kind().equals("dialogue")) {
                requireStoryRef(ctx, "dialogue", obj.targetId(), obj.pos());
            } else if (obj.kind().equals("quest")) {
                requireStoryRef(ctx, "quest", obj.targetId(), obj.pos());
            }
        }
        for (RewardNode reward : q.rewards()) {
            if (reward instanceof UnlockQuestRewardNode u) {
                requireStoryRef(ctx, "quest", u.questId(), u.pos());
            }
        }
    }

    private static void checkTrigger(ValidationContext ctx, TriggerDeclNode t) {
        if (t.when() instanceof WhenEntersNode w) {
            if (ZoneRegistry.get(w.zoneName()) == null) {
                ctx.error(w.pos(), "SME301", "Unknown zone '" + w.zoneName() + "' — register it via ZoneRegistry.register(...) before this reload");
            }
        } else if (t.when() instanceof WhenEventNode e) {
            checkEventName(ctx, e.eventName(), e.pos());
        }
        StmtWalker.walk(t.runBody(), visitor(ctx));
    }

    private static void checkEventName(ValidationContext ctx, String name, SourcePos pos) {
        if (!EventNameRegistry.isKnown(name)) {
            ctx.error(pos, "SME302", "Unknown event name '" + name + "' — register it via EventNameRegistry.register(...)");
        }
    }

    private static void requireStoryRef(ValidationContext ctx, String kind, String id, SourcePos pos) {
        ResourceLocation rl = ScriptingRegistryFacade.storyId(id);
        boolean declaredThisBatch = SmeSourceRegistry.isDeclared(kind, rl);
        boolean liveRegistered = switch (kind) {
            case "dialogue" -> ScriptingRegistryFacade.dialogue(id) != null;
            case "quest" -> ScriptingRegistryFacade.quest(id) != null;
            case "cutscene" -> ScriptingRegistryFacade.cutscene(id) != null;
            case "trigger" -> ScriptingRegistryFacade.trigger(id) != null;
            case "sequence" -> ScriptingRegistryFacade.sequence(id) != null;
            default -> false;
        };
        if (!declaredThisBatch && !liveRegistered) {
            ctx.error(pos, "SME303", "Unknown " + kind + " '" + id + "'");
        }
    }

    private static StmtWalker.Visitor visitor(ValidationContext ctx) {
        return new StmtWalker.Visitor() {
            @Override
            public void visitStmt(StmtNode stmt) {
                if (stmt instanceof StartQuestStmtNode s) {
                    requireStoryRef(ctx, "quest", s.questId(), s.pos());
                } else if (stmt instanceof StartDialogueStmtNode s) {
                    requireStoryRef(ctx, "dialogue", s.dialogueId(), s.pos());
                } else if (stmt instanceof PlayCinematicStmtNode s) {
                    requireStoryRef(ctx, "cutscene", s.cutsceneId(), s.pos());
                } else if (stmt instanceof CallStmtNode c) {
                    requireStoryRef(ctx, "sequence", c.sequenceId(), c.pos());
                } else if (stmt instanceof WaitEventStmtNode w) {
                    checkEventName(ctx, w.eventName(), w.pos());
                } else if (stmt instanceof JumpStmtNode j) {
                    requireCrossKindRef(ctx, j.targetId(), j.pos());
                }
            }
        };
    }

    /** A sequence/trigger-scope {@code jump <id>} resolves against dialogue → quest → cutscene → sequence, in that priority order — see {@code compiler.SequenceCompiler}. */
    private static void requireCrossKindRef(ValidationContext ctx, String id, SourcePos pos) {
        ResourceLocation rl = ScriptingRegistryFacade.storyId(id);
        for (String kind : new String[]{"dialogue", "quest", "cutscene", "sequence"}) {
            if (SmeSourceRegistry.isDeclared(kind, rl)) {
                return;
            }
        }
        if (ScriptingRegistryFacade.dialogue(id) != null || ScriptingRegistryFacade.quest(id) != null
                || ScriptingRegistryFacade.cutscene(id) != null || ScriptingRegistryFacade.sequence(id) != null) {
            return;
        }
        ctx.error(pos, "SME304", "'jump " + id + "' does not match any dialogue, quest, cutscene, or sequence id");
    }
}
