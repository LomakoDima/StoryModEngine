package com.dimalab.storymodengine.common.scripting.validation;

import com.dimalab.storymodengine.common.scripting.ast.StoryNode;

import java.util.ArrayList;
import java.util.List;

/** Runs every validation pass over one file's already-parsed {@link StoryNode}s, one story at a time, collecting each pass's diagnostics rather than stopping at the first failing pass — the same "collect, don't abort" discipline the lexer/parser already follow. */
public final class SmeValidator {

    private SmeValidator() {
    }

    public static List<StoryValidationResult> validate(String file, List<StoryNode> stories) {
        List<StoryValidationResult> results = new ArrayList<>();
        DuplicateIdPass.SeenInFile seenInFile = new DuplicateIdPass.SeenInFile();
        for (StoryNode story : stories) {
            ValidationContext ctx = new ValidationContext(file, story);
            DuplicateIdPass.run(ctx, seenInFile);
            VariableUsagePass.run(ctx);
            ReferenceResolutionPass.run(ctx);
            CommandArityPass.run(ctx);
            UnreachablePass.run(ctx);
            results.add(new StoryValidationResult(story, ctx.diagnostics(), ctx.varTypes()));
        }
        return results;
    }
}
