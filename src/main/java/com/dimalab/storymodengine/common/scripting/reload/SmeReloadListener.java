package com.dimalab.storymodengine.common.scripting.reload;

import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.compiler.SmeCompiler;
import com.dimalab.storymodengine.common.scripting.diagnostics.DiagnosticReporter;
import com.dimalab.storymodengine.common.scripting.lexer.LexResult;
import com.dimalab.storymodengine.common.scripting.lexer.SmeLexer;
import com.dimalab.storymodengine.common.scripting.parser.ParseResult;
import com.dimalab.storymodengine.common.scripting.parser.SmeParser;
import com.dimalab.storymodengine.common.scripting.registry.SmeSourceRegistry;
import com.dimalab.storymodengine.common.scripting.validation.SmeValidator;
import com.dimalab.storymodengine.common.scripting.validation.StoryValidationResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code .sme} files from {@code data/<namespace>/storymodengine/stories/*.sme} — the same
 * {@code AddReloadListenerEvent} timing as {@code DialogueJsonLoader}/{@code QuestJsonLoader}/{@code
 * CutsceneJsonLoader}, but extending vanilla's more general {@code SimplePreparableReloadListener}
 * rather than {@code SimpleJsonResourceReloadListener} — {@code .sme} is not JSON, and that class is
 * hard-wired to {@code Gson} parsing. A real, load-bearing deviation from the three existing loaders,
 * not an oversight.
 *
 * <p>Runs the whole reload batch through three passes, so cross-file references resolve regardless
 * of file processing order and one bad file never blocks the rest: (1) lex + parse every file; (2)
 * claim every successfully-parsed declaration's id into {@link SmeSourceRegistry} up front, so a
 * reference into a file processed later in this same batch still resolves; (3) validate, then
 * compile, each story that has zero {@code ERROR}-severity diagnostics — a story that fails
 * validation is reported but simply never registered, exactly like a hand-authored {@code
 * DialogueDefinition} that was never built.
 */
public final class SmeReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, String>> {

    private static final String DIRECTORY = "storymodengine/stories";

    @Override
    protected Map<ResourceLocation, String> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : resourceManager.listResources(DIRECTORY, loc -> loc.getPath().endsWith(".sme")).entrySet()) {
            try (InputStream in = entry.getValue().open()) {
                sources.put(entry.getKey(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                EngineLog.channel("SME").error("Failed to read " + entry.getKey(), e);
            }
        }
        return sources;
    }

    @Override
    protected void apply(Map<ResourceLocation, String> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        SmeSourceRegistry.reset();

        List<ParsedFile> parsedFiles = new ArrayList<>();
        int errorCount = 0;
        for (Map.Entry<ResourceLocation, String> entry : prepared.entrySet()) {
            String file = entry.getKey().toString();
            LexResult lex = SmeLexer.lex(file, entry.getValue());
            errorCount += DiagnosticReporter.report(file, lex.diagnostics());
            ParseResult parsed = SmeParser.parse(file, lex.tokens());
            errorCount += DiagnosticReporter.report(file, parsed.diagnostics());
            parsedFiles.add(new ParsedFile(file, parsed.stories()));
        }

        for (ParsedFile pf : parsedFiles) {
            for (StoryNode story : pf.stories()) {
                SmeSourceRegistry.claimAll(pf.file(), story);
            }
        }

        int storiesCompiled = 0;
        for (ParsedFile pf : parsedFiles) {
            for (StoryValidationResult result : SmeValidator.validate(pf.file(), pf.stories())) {
                errorCount += DiagnosticReporter.report(pf.file(), result.diagnostics());
                if (!result.hasErrors()) {
                    SmeCompiler.compile(pf.file(), result.story(), result.varTypes());
                    storiesCompiled++;
                }
            }
        }

        EngineLog.channel("SME").info("[reload] {} file(s) processed, {} stor{} compiled, {} error(s)",
                parsedFiles.size(), storiesCompiled, storiesCompiled == 1 ? "y" : "ies", errorCount);
    }

    private record ParsedFile(String file, List<StoryNode> stories) {
    }
}
