package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;
import com.dimalab.storymodengine.common.scripting.registry.SmeSourceRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Two independent checks per declared id: a within-this-file repeat, and a cross-file collision
 * (this file's declared id already owned by a <em>different</em> file, per the reload-wide claim
 * pass {@code SmeReloadListener}/{@code SmeSourceRegistry#claimAll} runs before validation).
 *
 * <p>{@code seenInFile} is shared across every story in one file — a {@code .sme} file can declare
 * more than one {@code story} block, and two different stories in the <em>same</em> file declaring
 * the same dialogue id must still be caught, even though {@link SmeSourceRegistry} only tracks the
 * first <em>file</em> to claim an id (both stories share that same file identity, so a per-file-only
 * check alone would never see the second declaration as a collision). {@link SmeValidator} owns one
 * {@code seenInFile} instance per file and passes it to every story's {@link #run} call.
 */
public final class DuplicateIdPass {

    private DuplicateIdPass() {
    }

    public static void run(ValidationContext ctx, SeenInFile seenInFile) {
        for (SmeNode decl : ctx.story().declarations()) {
            if (decl instanceof DialogueDeclNode d) {
                check(ctx, "dialogue", d.id(), d.pos(), seenInFile.dialogue);
            } else if (decl instanceof QuestDeclNode q) {
                check(ctx, "quest", q.id(), q.pos(), seenInFile.quest);
            } else if (decl instanceof TriggerDeclNode t) {
                check(ctx, "trigger", t.id(), t.pos(), seenInFile.trigger);
            } else if (decl instanceof SequenceDeclNode s && s.id() != null) {
                check(ctx, "sequence", s.id(), s.pos(), seenInFile.sequence);
            }
        }
    }

    private static void check(ValidationContext ctx, String kind, String id, SourcePos pos, Set<String> seenInThisFile) {
        if (!seenInThisFile.add(id)) {
            ctx.error(pos, "SME200", "Duplicate " + kind + " id '" + id + "' — already declared earlier in this file");
            return;
        }
        ResourceLocation rl = ScriptingRegistryFacade.storyId(id);
        String owner = SmeSourceRegistry.ownerFile(kind, rl);
        if (owner != null && !owner.equals(ctx.file())) {
            ctx.error(pos, "SME201", "Duplicate " + kind + " id '" + id + "' — already declared in " + owner);
        }
    }

    /** One per file (not per story) — see the class doc for why this can't just be a local inside {@link #run}. */
    public static final class SeenInFile {
        private final Set<String> dialogue = new HashSet<>();
        private final Set<String> quest = new HashSet<>();
        private final Set<String> trigger = new HashSet<>();
        private final Set<String> sequence = new HashSet<>();
    }
}
