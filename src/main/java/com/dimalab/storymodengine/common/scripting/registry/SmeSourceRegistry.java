package com.dimalab.storymodengine.common.scripting.registry;

import com.dimalab.storymodengine.common.scripting.ast.DialogueDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.QuestDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.SequenceDeclNode;
import com.dimalab.storymodengine.common.scripting.ast.SmeNode;
import com.dimalab.storymodengine.common.scripting.ast.StoryNode;
import com.dimalab.storymodengine.common.scripting.ast.TriggerDeclNode;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@code .sme} file first declared each dialogue/quest/trigger/sequence id during the
 * current reload — reset at the start of every reload by {@code SmeReloadListener}, then populated
 * by a first pass over every successfully-parsed file before any per-file validation runs. This is
 * what lets {@code DuplicateIdPass} name the <em>other</em> file in a collision message, and lets
 * {@code ReferenceResolutionPass} resolve a reference into a file processed later in the same reload
 * batch (a plain per-file live-registry lookup could not, since that file hasn't compiled yet).
 *
 * <p><b>Test isolation</b>: this is a live, process-global singleton — the same instance the running
 * server's real reloads populate. Anything that re-validates already-shipped content outside of a
 * real reload (e.g. {@code debug.ScriptTestCommand}'s pure-JVM checks) must {@link #snapshot()} the
 * current state, {@link #reset()}, run its own {@link #claimAll} + validation, then {@link
 * #restore(Map)} the original state — otherwise its own re-validated ids collide with whatever the
 * live server already claimed under the real file's own identity string, and everything not
 * literally identical in both content and claimed-file-name misreports as a duplicate.
 */
public final class SmeSourceRegistry {

    private static final Map<String, String> OWNERS = new ConcurrentHashMap<>();

    private SmeSourceRegistry() {
    }

    public static void reset() {
        OWNERS.clear();
    }

    /** A copy of the current claims, for a caller that needs to temporarily replace and later restore live state (see the class doc's test-isolation note). */
    public static Map<String, String> snapshot() {
        return new HashMap<>(OWNERS);
    }

    public static void restore(Map<String, String> snapshot) {
        OWNERS.clear();
        OWNERS.putAll(snapshot);
    }

    /** Registers {@code file} as the owner of {@code id} within {@code kind}'s namespace, unless something already claimed it — first claim wins. */
    public static void claim(String kind, ResourceLocation id, String file) {
        OWNERS.putIfAbsent(key(kind, id), file);
    }

    public static String ownerFile(String kind, ResourceLocation id) {
        return OWNERS.get(key(kind, id));
    }

    public static boolean isDeclared(String kind, ResourceLocation id) {
        return OWNERS.containsKey(key(kind, id));
    }

    /** Claims every dialogue/quest/trigger/sequence id {@code story} declares, under {@code file} — the exact shape {@code SmeReloadListener} runs once per file, per reload, before validation; shared here so {@code ScriptTestCommand} can run the identical claim step against its own temporary content. */
    public static void claimAll(String file, StoryNode story) {
        for (SmeNode decl : story.declarations()) {
            if (decl instanceof DialogueDeclNode d) {
                claim("dialogue", ScriptingRegistryFacade.storyId(d.id()), file);
            } else if (decl instanceof QuestDeclNode q) {
                claim("quest", ScriptingRegistryFacade.storyId(q.id()), file);
            } else if (decl instanceof TriggerDeclNode t) {
                claim("trigger", ScriptingRegistryFacade.storyId(t.id()), file);
            } else if (decl instanceof SequenceDeclNode s && s.id() != null) {
                claim("sequence", ScriptingRegistryFacade.storyId(s.id()), file);
            }
        }
    }

    private static String key(String kind, ResourceLocation id) {
        return kind + ":" + id;
    }
}
